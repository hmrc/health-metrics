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

import cats.implicits.*
import play.api.Logging
import play.api.libs.json.JsValue
import uk.gov.hmrc.healthmetrics.connector.{ReleasesConnector, SlackNotificationsConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.healthmetrics.model.*
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutdatedDeploymentNotifierService @Inject()(
  releasesConnector            : ReleasesConnector,
  slackNotificationsConnector  : SlackNotificationsConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
)(using
  ec: ExecutionContext
) extends Logging:

  def notify()(using hc: HeaderCarrier): Future[Unit] =
    for
      teams        <- teamsAndRepositoriesConnector.allTeams()
      releases     <- releasesConnector.releases()
      outdated      = releases.flatMap: wrw =>
                        val latest = wrw.deployments.maxBy(_.version).version
                        wrw.deployments.collect:
                          case d if d.environment != Environment.Production && d.version < latest =>
                            (wrw.serviceName, d.environment, d.version, latest)
      byTeam        = teams
                        .map( team => (
                          team.name,
                          outdated
                            .filter((serviceName, _, _, _) => team.repos.contains(serviceName.asString))
                        ))
      responses    <- byTeam.toList.foldLeftM(Seq.empty[SlackNotificationsConnector.Response]):
                        case (acc, (teamName, outdatedServices)) =>
                          slackNotificationsConnector
                            .sendMessage(
                              outdatedDeploymentsMessage(
                                SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName),
                                teamName,
                                outdatedServices
                              )
                            )
                            .map(acc :+ _)
    yield logger.info(s"Completed sending ${responses.length} Slack messages for outdated deployments")


  private def outdatedDeploymentsMessage(
    channelLookup: SlackNotificationsConnector.ChannelLookup,
    teamName: TeamName,
    outdated: Seq[(ServiceName, Environment, Version, Version)]
  ): SlackNotificationsConnector.Request =

    val block1: JsValue =
      SlackNotificationsConnector.mrkdwnBlock(
        s"Hello ${teamName.asString}, \\nSome of your services are running outdated versions in some environments. \\n\\nPlease undeploy them in the environment if that environment is not being used, or deploy the latest version to that environment."
      )

    val bulletLines: String =
      outdated.map:
        case (service, env, deployed, latest) =>
          s"• <https://catalogue.tax.service.gov.uk/repositories/${service.asString}|${service.asString}> is running $deployed in ${env.asString} (latest: $latest)."
      .mkString("\\n")

    val block2: JsValue =
      SlackNotificationsConnector.mrkdwnBlock(bulletLines)

    SlackNotificationsConnector.Request(
      channelLookup   = channelLookup,
      displayName     = "MDTP Catalogue",
      emoji           = ":tudor-crown:",
      text            = "Some services have outdated versions deployed in lower environments",
      blocks          = Seq(block1, block2),
      callbackChannel = Some("team-platops-alerts")
    )
