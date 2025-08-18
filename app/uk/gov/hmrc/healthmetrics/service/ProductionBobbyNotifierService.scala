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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.healthmetrics.connector.{TeamsAndRepositoriesConnector, ServiceDependenciesConnector, SlackNotificationsConnector}
import uk.gov.hmrc.healthmetrics.model.{SlugInfoFlag, RepoName, TeamName}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProductionBobbyNotifierService @Inject()(
  teamsAndReposConnector      : TeamsAndRepositoriesConnector
, serviceDependenciesConnector: ServiceDependenciesConnector
, slackNotificationsConnector : SlackNotificationsConnector
)(using
  ec: ExecutionContext
) extends Logging:

  def notify()(using hc: HeaderCarrier): Future[Unit] =
    val now = java.time.LocalDate.now()
    for
      reports         <- serviceDependenciesConnector.bobbyReports(flag = SlugInfoFlag.Production)
      repoMap         <- teamsAndReposConnector.allRepos().map(_.map(x => (x.repoName, x.teamNames.sorted.toList)).toMap)
      filteredReports =  reports.filter(_.violations.exists(v => !v.exempt && now.isAfter(v.from)))
      groupedByTeam   =  repoMap.flatMap:
                           case (repoName, teams) => filteredReports.find(_.repoName == repoName).map(report => teams.map(_ -> report.repoName))
                         .flatten.groupMap(_._1)(_._2).toMap
      responses       <- groupedByTeam.toList.foldLeftM(List.empty[(TeamName, SlackNotificationsConnector.Response)]):
                           (acc, teamReports) =>
                             val (team, repos) = teamReports
                             slackNotificationsConnector
                               .sendMessage(errorNotification(team, repos.toSet))
                               .map(resp => acc :+ (team, resp))
      _               =  responses.map:
                            case (team, rsp) if rsp.errors.nonEmpty => logger.warn(s"Sending Bobby Error message to ${team.asString} had errors ${rsp.errors.mkString(" : ")}")
                            case (team, _)                          => logger.info(s"Successfully sent Bobby Error message to ${team.asString}")
    yield ()

  private def errorNotification(teamName: TeamName, repos: Set[RepoName]): SlackNotificationsConnector.Request =
    val heading = SlackNotificationsConnector.mrkdwnBlock:
      ":alarm: ACTION REQUIRED! :platops-bobby:"

    val msg = SlackNotificationsConnector.mrkdwnBlock:
      s"Hello ${teamName.asString}, the following services are deployed in Production and are in violation of one or more Bobby Rule:"

    val warnings =
      repos
        .toList
        .map(_.asString)
        .sorted
        .grouped(10)
        .map: batch =>
          SlackNotificationsConnector.mrkdwnBlock:
            batch
              .map(repo => s"• <https://catalogue.tax.service.gov.uk/service/$repo#environmentTabs|$repo>")
              .mkString("\\n")
        .toSeq

    val link = SlackNotificationsConnector.mrkdwnBlock:
      s"To stay informed on upcoming Bobby Rules that affect your services, visit your <https://catalogue.tax.service.gov.uk/teams/${teamName.asString}|Team Page> in the Catalogue."

    SlackNotificationsConnector.Request(
      channelLookup   = SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName),
      displayName     = "MDTP Catalogue",
      emoji           = ":tudor-crown:",
      text            = "There are Bobby Rules being violated by your service(s) deployed in Production",
      blocks          = Seq(heading, msg) ++ warnings :+ link,
      callbackChannel = Some("team-platops-alerts")
    )

