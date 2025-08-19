/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.healthmetrics.service

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Logging
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.healthmetrics.model.SlugInfoFlag
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.healthmetrics.connector.{SlackNotificationsConnector, VulnerabilitiesConnector}
import uk.gov.hmrc.healthmetrics.model.TeamName

@Singleton
class ProductionVulnerabilitiesNotifierService @Inject()(
  vulnerabilitiesConnector   : VulnerabilitiesConnector,
  slackNotificationsConnector: SlackNotificationsConnector
)(using
  ec: ExecutionContext
) extends Logging:

  def notify()(using hc: HeaderCarrier): Future[Unit] =
    for
      vulnerabilities <- vulnerabilitiesConnector.vulnerabilitySummaries(flag = Some(SlugInfoFlag.Production.asString))
      teamsToNotify   =  vulnerabilities
                           .flatMap(_.occurrences)
                           .flatMap(occurrence => occurrence.teams.map(_ -> Set(occurrence.service)))
                           .groupMapReduce(_._1)(_._2)(_.union(_))
      responses       <- teamsToNotify.toList.foldLeftM(List.empty[(TeamName, SlackNotificationsConnector.Response)]):
                           (acc, teamServices) =>
                            val (team, services) = teamServices
                             slackNotificationsConnector
                               .sendMessage(errorNotification(team, services))
                               .map(resp => acc :+ (team, resp))
      _               =  responses.map:
                           case (team, rsp) if rsp.errors.nonEmpty => logger.warn(s"Sending Vulnerabilities in Production message to $team had errors ${rsp.errors.mkString(" : ")}")
                           case (team, _)                          => logger.info(s"Successfully sent Vulnerabilities in Production message to $team")
    yield ()

  private def errorNotification(teamName: TeamName, services: Set[String]): SlackNotificationsConnector.Request =
    val heading = SlackNotificationsConnector.mrkdwnBlock:
      ":alarm: ACTION REQUIRED! :alarm:"

    val msg = SlackNotificationsConnector.mrkdwnBlock:
      s"Hello ${teamName.asString}, the following services are deployed in Production and have vulnerabilities marked as `Action Required`"

    val warnings =
      services
        .toList
        .sorted
        .grouped(10)
        .map { batch =>
          val batchMsg = batch
            .map(service => s"• <https://catalogue.tax.service.gov.uk/service/$service#environmentTabs|$service>")
            .mkString("\\n")
          SlackNotificationsConnector.mrkdwnBlock(batchMsg)
        }
        .toSeq

    val link = SlackNotificationsConnector.mrkdwnBlock:
      s"To stay informed on vulnerabilities that are affecting your services, visit your <https://catalogue.tax.service.gov.uk/teams/${teamName.asString}|Team Page> in the Catalogue."

    SlackNotificationsConnector.Request(
      channelLookup   = SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName),
      displayName     = "MDTP Catalogue",
      emoji           = ":tudor-crown:",
      text            = "There are service(s) owned by you that are deployed in Production with vulnerabilities marked as action required!",
      blocks          = Seq(heading, msg) ++ warnings :+ link,
      callbackChannel = Some("team-platops-alerts")
    )
