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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.healthmetrics.connector.{SlackNotificationsConnector,ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.healthmetrics.model.TeamName

@Singleton
class PlatformInitiativesNotifierService @Inject()(
  serviceDependenciesConnector : ServiceDependenciesConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  slackNotificationsConnector  : SlackNotificationsConnector
)(using
  ec: ExecutionContext
) extends Logging:

  def notify()(using hc: HeaderCarrier): Future[Unit] =
    val oldArtefacts = Seq("com.typesafe.play" -> "play", "org.scala-lang" -> "scala-library")
    val recommendedJdk = "21.0"

    for      
      reposOnOldJdk        <- serviceDependenciesConnector
                                .getSlugJdkVersions()
                                .map: repos =>
                                  repos.filterNot(_.version.startsWith(recommendedJdk))
                                .map(_.map(_.name))
      teamsOnOldJdk        <- teamsAndRepositoriesConnector.allRepos()
                                .map(_.filter(r => reposOnOldJdk.contains(r.repoName.asString)))
                                .map(_.flatMap(_.teamNames))
                              .map(_.toSet)
      teamsOnScala2OrPlay2 <- oldArtefacts.flatTraverse:
                                case (group, artefact) =>
                                  serviceDependenciesConnector
                                    .getTeams(group, artefact)
                                    .map(_.flatMap(_.teamNames))
                              .map(_.toSet)
      teamsToNotify        = teamsOnScala2OrPlay2 ++ teamsOnOldJdk
      responses            <- teamsToNotify.toList.foldLeftM(List.empty[(TeamName, SlackNotificationsConnector.Response)]):
                               (acc, teamNames) =>
                                 val team = teamNames
                                 slackNotificationsConnector
                                   .sendMessage(initiativeNotification(team))
                                   .map(resp => acc :+ (team, resp))
      _                    =  responses.map:
                             case (team, rsp) if rsp.errors.nonEmpty => logger.warn(s"Sending Platform Initiatives monthly reminder to $team had errors ${rsp.errors.mkString(" : ")}")
                             case (team, _)                          => logger.info(s"Successfully sent Platform Initiatives monthly reminder to $team")
    yield ()

  private def initiativeNotification(teamName: TeamName): SlackNotificationsConnector.Request =
    val msg = SlackNotificationsConnector.mrkdwnBlock:
      s"Hello ${teamName.asString}, <https://catalogue.tax.service.gov.uk/platform-initiatives?team=${teamName.asString}|Platform Initiatives> " +
        s"has recommendations for respositories you own, please review and address."

    SlackNotificationsConnector.Request(
      channelLookup   = SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName),
      displayName     = "MDTP Catalogue",
      emoji           = ":tudor-crown:",
      text            = "Repo(s) owned by you do not meet platform recommendations",
      blocks          = Seq(msg),
      callbackChannel = Some("team-platops-alerts")
    )
