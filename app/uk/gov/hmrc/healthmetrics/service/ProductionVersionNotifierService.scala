/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.healthmetrics.connector.{ReleasesConnector, SlackNotificationsConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.healthmetrics.model.{Environment, ServiceName, TeamName}

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.healthmetrics.connector.ReleasesConnector.WhatsRunningWhere

@Singleton
class ProductionVersionNotifierService @Inject()(
  configuration              : Configuration
, releasesConnector          : ReleasesConnector
, slackNotificationsConnector: SlackNotificationsConnector
, teamsAndReposConnector     : TeamsAndRepositoriesConnector
)(using ExecutionContext
) extends Logging:

  given HeaderCarrier = HeaderCarrier()

  private val minimumDeploymentAge =
    configuration.get[Duration]("production-version-slack-notifier.minimumDeploymentAge")

  def notify(runTime: Instant): Future[Unit] =
      for
        teams           <- teamsAndReposConnector.allTeams()
        releases        <- releasesConnector.releases()
        timeLimit       =  runTime
                             .truncatedTo(ChronoUnit.DAYS)
                             .minus(minimumDeploymentAge.toDays, ChronoUnit.DAYS)
        prodBehind      =  releases.filter: wrw =>
                             ( wrw.deployments.maxBy(_.version)
                             , wrw.deployments.find(_.environment == Environment.Production)
                             ) match
                               case (d1, Some(d2)) => d1.version > d2.version             &&
                                                      d1.lastDeployed.isBefore(timeLimit) &&
                                                      d2.lastDeployed.isBefore(timeLimit)
                               case _              => false
        notifyTeams     =  teams
                             .map: team =>
                               (team.name, prodBehind.filter(x => team.repos.contains(x.serviceName.asString)))
                             .filter:
                               case (_, deployments) => deployments.nonEmpty
        _               =  logger.info(s"There are ${notifyTeams.size} teams with a total of ${notifyTeams.flatMap(_._2.map(_.serviceName)).distinct.size} services with a lower production version than in other environments to send slack notifications for.")
        slackResponses  <- notifyTeams
                             .foldLeftM(List.empty[(TeamName, Seq[ReleasesConnector.WhatsRunningWhere], SlackNotificationsConnector.Response)]):
                               case (acc, (teamName, wrw)) =>
                                 slackNotificationsConnector
                                  .sendMessage(lowerVersionInProduction(teamName, wrw.map(_.serviceName), minimumDeploymentAge))
                                  .map(resp => acc :+ (teamName, wrw, resp))
        _               =  slackResponses.map:
                             case (teamName, wrw, response)
                               if response.errors.nonEmpty  => logger.warn(s"Sending lower version in Production Warning message to ${teamName.asString} for ${wrw.map(_.serviceName.asString).mkString(", ")} had errors ${response.errors.mkString(" : ")}")
                             case (teamName, wrw, _       ) => logger.info(s"Successfully sent lower version in Production Warning message to ${teamName.asString} for ${wrw.map(_.serviceName.asString).mkString(", ")}")
      yield logger.info("Completed sending Slack messages for lower version in Production Warnings")

  import uk.gov.hmrc.http.StringContextOps
  private[service] def lowerVersionInProduction(teamName: TeamName, serviceNames: Seq[ServiceName], minimumDeploymentAge: Duration): SlackNotificationsConnector.Request =
    SlackNotificationsConnector.Request(
      channelLookup   = SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName)
    , displayName     = "MDTP Catalogue"
    , emoji           = ":tudor-crown:"
    , text            = s"${teamName.asString} has repositories with Production versions that are lower than other environments."
    , blocks          = Seq:
                          SlackNotificationsConnector.mrkdwnBlock:
                            val url = url"https://catalogue.tax.service.gov.uk/whats-running-where?teamName=${teamName.asString}"
                            s"${teamName.asString}, please check <$url|What's Running Where>." +
                              s"\\n\\nThe following services belonging to your team have had a lower version in Production than in some other environments for at least $minimumDeploymentAge." +
                              s"\\n\\n${serviceNames.map(r => s"`${r.asString}`").mkString("\\n\\n")}"
    , callbackChannel = Some("team-platops-alerts")
    )
