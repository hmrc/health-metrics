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
import uk.gov.hmrc.healthmetrics.connector.{ServiceConfigsConnector, ServiceDependenciesConnector, SlackNotificationsConnector}
import uk.gov.hmrc.healthmetrics.model.{ServiceName, TeamName}

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpcomingBobbyNotifierService @Inject()(
  serviceConfigsConnector      : ServiceConfigsConnector
, serviceDependenciesConnector : ServiceDependenciesConnector
, slackNotificationsConnector  : SlackNotificationsConnector
, configuration                : Configuration
)(using
  ec: ExecutionContext
) extends Logging:

  private given HeaderCarrier = HeaderCarrier()

  private val futureDatedRuleWindow =
    configuration.get[Duration]("upcoming-bobby-notifier.ruleNotificationWindow")

  extension (localDate: LocalDate)
    def toInstant: Instant = localDate.atStartOfDay().toInstant(ZoneOffset.UTC)

  def notify(runTime: Instant): Future[Unit] =
    val endWindow = LocalDate.now().toInstant.plus(futureDatedRuleWindow.toDays, ChronoUnit.DAYS)
    for
      futureDatedRules          <- serviceConfigsConnector
                                      .getBobbyRules()
                                      .map(_.libraries.filter(rule => rule.from.toInstant.isAfter(runTime) && rule.from.toInstant.isBefore(endWindow)))
      _                         =  logger.info(s"There are ${futureDatedRules.size} future dated Bobby rules becoming active in the next [$futureDatedRuleWindow] to send slack notifications for.")
      rulesWithAffectedServices <- futureDatedRules.foldLeftM(List.empty[(TeamName, (ServiceName, ServiceConfigsConnector.BobbyRules.BobbyRule))]):
                                      (acc, rule) =>
                                        serviceDependenciesConnector
                                          .getAffectedServices(group = rule.group, artefact = rule.artefact, versionRange = rule.versionRange)
                                          .map(_.filterNot(x => rule.exemptProjects.contains(x.serviceName.asString)))
                                          .map(sds => acc ++ sds.flatMap(sd => sd.teamNames.map(team => (team, (sd.serviceName, rule)))))
      grouped                   =  rulesWithAffectedServices.groupMap(_._1)(_._2).toList
      slackResponses            <- grouped.foldLeftM(List.empty[(TeamName, SlackNotificationsConnector.Response)]):
                                      case (acc, (teamName, drs)) =>
                                        slackNotificationsConnector
                                          .sendMessage(bobbyWarning(SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName), teamName, drs))
                                          .map(resp => acc :+ (teamName, resp))
      _                         =  slackResponses.map:
                                      case (teamName, rsp) if rsp.errors.nonEmpty => logger.warn(s"Sending Bobby Warning message to ${teamName.asString} had errors ${rsp.errors.mkString(" : ")}")
                                      case (teamName, _)                          => logger.info(s"Successfully sent Bobby Warning message to ${teamName.asString}")
    yield logger.info("Completed sending Slack messages for Bobby Warnings")

  private def bobbyWarning(channelLookup: SlackNotificationsConnector.ChannelLookup, teamName: TeamName, warnings: List[(ServiceName, ServiceConfigsConnector.BobbyRules.BobbyRule)]): SlackNotificationsConnector.Request =
      val msg =
        SlackNotificationsConnector.mrkdwnBlock(s"Hello ${teamName.asString}, please be aware that the following builds will fail soon because of upcoming Bobby Rules:")

      val ruleTexts = warnings.map:
        case (serviceName, rule) =>
          s"`${serviceName.asString}` will fail from *${rule.from}* with dependency on ${rule.group}.${rule.artefact} ${rule.versionRange} - see <https://catalogue.tax.service.gov.uk/repositories/${serviceName.asString}#environmentTabs|Catalogue>"

      // Slack API limits: 50 blocks total, 3000 chars per text block
      // With ~300 chars per service -> rule msg, we can safely fit 7 rules per block
      // This means we can safely handle Teams with up to 343 (7 * 49 remaining blocks) bobby warnings
      val RULES_PER_BLOCK = 7

      val ruleBlocks = ruleTexts
        .grouped(RULES_PER_BLOCK)
        .map(_.mkString("\\n")) // literal \n so it's sent to slack
        .map(SlackNotificationsConnector.mrkdwnBlock(_))
        .toList

      SlackNotificationsConnector.Request(
        channelLookup   = channelLookup,
        displayName     = "MDTP Catalogue",
        emoji           = ":tudor-crown:",
        text            = "There are upcoming Bobby Rules affecting your service(s)",
        blocks          = msg :: ruleBlocks,
        callbackChannel = Some("team-platops-alerts")
      )
