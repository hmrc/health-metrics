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
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.healthmetrics.connector.{ServiceConfigsConnector, SlackNotificationsConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.healthmetrics.model.{ServiceName, TeamName, RepoName}

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CallingDeprecatedServiceNotifierService @Inject()(
  serviceConfigsConnector      : ServiceConfigsConnector
, slackNotificationsConnector  : SlackNotificationsConnector
, teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
)(using
  ec: ExecutionContext
) extends Logging:

  def notify()(using hc: HeaderCarrier): Future[Unit] =
    for
      repositories          <- teamsAndRepositoriesConnector.allRepos()
      eolRepositories       =  repositories.filter(_.isDeprecated)
      serviceRelationships  <- eolRepositories
                                .foldLeftM(Seq.empty[(TeamsAndRepositoriesConnector.Repo, Set[ServiceName])]):
                                  case (acc, eolRepo) =>
                                    serviceConfigsConnector
                                      .getServiceRelationships(ServiceName(eolRepo.repoName.asString))
                                      .map:
                                        case Some(relationships) => acc :+ (eolRepo, relationships.inboundServices)
                                        case None                => acc
      responses             <- serviceRelationships
                                .foldLeftM(Seq.empty[SlackNotificationsConnector.Response]):
                                  case (acc, (repo, relationships)) =>
                                      val enrichRelationships = repositories.filter(repo => relationships.map(_.asString).contains(repo.repoName.asString))
                                      enrichRelationships
                                        .flatMap(_.teamNames)
                                        .distinct
                                        .map(name => name -> enrichRelationships.filter(_.teamNames.contains(name)))
                                        .foldLeftM(Seq.empty[SlackNotificationsConnector.Response]):
                                          case (acc, (teamName, repos)) =>
                                            slackNotificationsConnector
                                              .sendMessage(downstreamMarkedAsDeprecated(SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName), teamName, repo.repoName, repo.endOfLifeDate, repos.map(_.repoName)))
                                              .map(acc :+ _)
                                        .map(acc ++ _)
    yield logger.info(s"Completed sending ${responses.length} Slack messages for deprecated repositories")

  private def downstreamMarkedAsDeprecated(channelLookup: SlackNotificationsConnector.ChannelLookup, teamName: TeamName, eolRepository: RepoName, eol: Option[Instant], impactedRepositories: Seq[RepoName]): SlackNotificationsConnector.Request =
    import play.api.libs.json.{JsValue, Json}
    val repositoryHref: String = s"<https://catalogue.tax.service.gov.uk/repositories/${eolRepository.asString}|${eolRepository.asString}>"
    val deprecatedText: String =
      eol match
        case Some(date) =>
          val utc = ZoneId.of("UTC")
          val eolFormatted = date.atZone(utc).toLocalDate.format(DateTimeFormatter.ofPattern("dd MMM uuuu"))
          s"$repositoryHref is marked as deprecated with an end of life date of `$eolFormatted`."
        case _          => s"$repositoryHref is marked as deprecated."

    val repositoryElements: Seq[JsValue] =
      impactedRepositories.map: repoName =>
        Json.parse:
          s"""{
            "type": "rich_text_section",
            "elements": [{
              "type": "link",
              "url": "https://catalogue.tax.service.gov.uk/repositories/${repoName.asString}",
              "text": "${repoName.asString}"
            }]
          }"""

    val block1: JsValue =
      SlackNotificationsConnector.mrkdwnBlock(s"Hello ${teamName.asString}, \\n$deprecatedText")

    val block2 = Json.parse:
      s"""{
        "type": "rich_text",
        "elements": [{
          "type": "rich_text_section",
          "elements": [{
            "type": "text",
            "text": "The following services may have a dependency on this repository:"
          }]
        }, {
          "type": "rich_text_list",
          "style": "bullet",
          "elements": ${Json.toJson(repositoryElements)}
        }]
      }"""

    SlackNotificationsConnector.Request(
      channelLookup   = channelLookup,
      displayName     = "MDTP Catalogue",
      emoji           = ":tudor-crown:",
      text            = s"A downstream service has been marked as deprecated",
      blocks          = Seq(block1, block2),
      callbackChannel = Some("team-platops-alerts")
    )
