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

import uk.gov.hmrc.healthmetrics.connector.{SlackNotificationsConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.healthmetrics.connector.TeamsAndRepositoriesConnector.{BuildData, BuildResult, TestType}
import uk.gov.hmrc.healthmetrics.model.{RepoName, TeamName}
import uk.gov.hmrc.healthmetrics.service.InactiveTestRepoNotifierService.InactiveTestRepo
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.all.*
import play.api.libs.json.JsValue
import play.api.{Configuration, Logging}


@Singleton
class InactiveTestRepoNotifierService @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, slackNotificationsConnector  : SlackNotificationsConnector
, configuration                : Configuration
)(using
  ec: ExecutionContext
) extends Logging:

  private val oldBuildCutoff    = configuration.get[Duration]("inactive-test-repositories-jobs.oldBuildCutoff")
  private val acceptanceCutoff  = configuration.get[Duration]("inactive-test-repositories-jobs.acceptanceCutoff")
  private val failedBuildCutoff = configuration.get[Duration]("inactive-test-repositories-jobs.failedBuildCutoff")

  def notify(now: Instant)(using hc: HeaderCarrier): Future[Unit] =
    for
      allTestRepos     <- teamsAndRepositoriesConnector.allTestRepos()
      allTestJobs      <- teamsAndRepositoriesConnector.allTestJobs()
      reposWithOldJobs =  allTestJobs.flatMap: job =>
                            val owningTeams = allTestRepos.find(_.repoName == job.repoName).map(_.owningTeams).getOrElse(Seq.empty[TeamName])
                            job.latestBuild match
                              // 360+ days, all tests (any build status)
                              case Some(build) if build.timestamp.isBefore(now.minus(oldBuildCutoff))
                                               => build.result.map: _ =>
                                                    InactiveTestRepo.fromJob(job.repoName, job.jobName, job.jenkinsUrl, owningTeams, Some(oldBuildCutoff))

                              // 90+ days, Acceptance tests (any build status)
                              case Some(build) if build.timestamp.isBefore(now.minus(acceptanceCutoff)) && job.testType.contains(TestType.Acceptance)
                                               => build.result.map: _ =>
                                                    InactiveTestRepo.fromJob(job.repoName, job.jobName, job.jenkinsUrl, owningTeams, Some(acceptanceCutoff))

                              // 30+ days, other tests (only FAILURE or UNSTABLE or ABORTED)
                              case Some(build) if build.timestamp.isBefore(now.minus(failedBuildCutoff))
                                               && !job.testType.contains(TestType.Performance)
                                               && !job.testType.contains(TestType.Acceptance)
                                               => build.result
                                                    .filter: result =>
                                                      Set(BuildResult.Failure, BuildResult.Unstable, BuildResult.Aborted).contains(result)
                                                    .map: result =>
                                                      InactiveTestRepo.fromJob(job.repoName, job.jobName, job.jenkinsUrl, owningTeams, Some(failedBuildCutoff))
                              // No build record
                              case None        => Some(InactiveTestRepo.fromJob(job.repoName, job.jobName, job.jenkinsUrl, owningTeams))
                              case _           => None
      reposWithNoJobs  =  allTestRepos
                            .filterNot(testRepo => allTestJobs.map(_.repoName).contains(testRepo.repoName))
                            .map: repo =>
                              InactiveTestRepo(repo.repoName, s"<https://catalogue.tax.service.gov.uk/repositories/${repo.repoName}|*${repo.repoName}*> has no test job defined in Jenkins Github repositories.", repo.owningTeams)
      filteredByCip    =  (reposWithOldJobs ++ reposWithNoJobs).filterNot(_.owningTeams.exists(t => t.asString.split("\\s+").contains("CIP"))) // built off platform
      groupedByTeam    =  filteredByCip
                            .flatMap: repo =>
                              repo.owningTeams.map(team => Map(team -> Seq(repo)))
                            .combineAll
      responses        <- groupedByTeam.toList.foldLeftM(List.empty[(TeamName, SlackNotificationsConnector.Response)]):
                            (acc, inactiveTestRepos) =>
                              val (team, testRepos) = inactiveTestRepos
                              slackNotificationsConnector
                                .sendMessage(infoNotification(team, testRepos))
                                .map(resp => acc :+ (team, resp))
      _               =  responses.map:
                           case (team, rsp) if rsp.errors.nonEmpty => logger.warn(s"Sending Inactive Test Repository message to ${team.asString} had errors ${rsp.errors.mkString(" : ")}")
                           case (team, _)                          => logger.info(s"Successfully sent Inactive Test Repository message to ${team.asString}")
    yield ()

  private def infoNotification(teamName: TeamName, testRepos: Seq[InactiveTestRepo]): SlackNotificationsConnector.Request =
    val msg = SlackNotificationsConnector.mrkdwnBlock:
      s"Hello *${teamName.asString}*, the following test repositories may be inactive please review:"

    val messages =
      testRepos
        .toList
        .sortBy(_.repoName.asString)
        .grouped(5)
        .map: batch =>
          SlackNotificationsConnector.mrkdwnBlock:
            batch
              .map(testRepo => s"${testRepo.message}")
              .mkString("\\n\\n")
        .toSeq

    val actions =
      SlackNotificationsConnector.mrkdwnBlock(
        Seq(
          "*Next Steps*"
        , s"• Review and decommission any inactive test repositories."
        , s"• Remove related jobs before deleting or archiving repositories."
        , s"• Keep track of your team's Test Results, visit the <https://catalogue.tax.service.gov.uk/tests?teamName=${teamName.asString}|Test Results Page> in the Catalogue."
        ).mkString("\\n")
      )

    SlackNotificationsConnector.Request(
      channelLookup   = SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName),
      displayName     = "MDTP Catalogue",
      emoji           = ":tudor-crown:",
      text            = "The test repositories may be inactive",
      blocks          = Seq(msg) ++ SlackNotificationsConnector.withDivider(messages ++ Seq(actions)),
      callbackChannel = Some("team-platops-alerts")
    )

object InactiveTestRepoNotifierService:
  case class InactiveTestRepo(
    repoName   : RepoName
  , message    : String
  , owningTeams: Seq[TeamName]
  )

  object InactiveTestRepo:
    private def buildMessage(
      repoName   : RepoName
    , jobName    : String
    , jenkinsUrl : String
    , duration   : Option[Duration]
    ): String =
      duration match
        case Some(duration) => s"<https://catalogue.tax.service.gov.uk/repositories/$repoName|*$repoName*> has a test job: <$jenkinsUrl|*$jobName*> that hasn't run in ${duration.toDays.toInt} days."
        case None           => s"<https://catalogue.tax.service.gov.uk/repositories/$repoName|*$repoName*> has a test job: <$jenkinsUrl|*$jobName*> that has no build record."

    def fromJob(
      repoName   : RepoName
    , jobName    : String
    , jenkinsUrl : String
    , owningTeams: Seq[TeamName]
    , duration   : Option[Duration] = None
    ): InactiveTestRepo =
      val message = buildMessage(repoName, jobName, jenkinsUrl, duration)
      InactiveTestRepo(repoName, message, owningTeams)
