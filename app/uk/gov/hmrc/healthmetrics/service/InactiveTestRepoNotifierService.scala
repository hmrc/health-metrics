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

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.all.*
import play.api.Logging

@Singleton
class InactiveTestRepoService @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, slackNotificationsConnector  : SlackNotificationsConnector
)(using
  ec: ExecutionContext
) extends Logging:

  val failedBuildCutoff = Instant.now().minus(30, ChronoUnit.DAYS)
  val acceptanceCutoff  = Instant.now().minus(90, ChronoUnit.DAYS)
  val oldBuildCutoff    = Instant.now().minus(360, ChronoUnit.DAYS)

  def notify(using hc: HeaderCarrier): Future[Unit] = 
    for
      allTestRepos     <- teamsAndRepositoriesConnector.allTestRepos()
      allTestJobs      <- teamsAndRepositoriesConnector.allTestJobs()
      reposWithOldJobs =  allTestJobs.flatMap: job =>
                            val owningTeams = allTestRepos.find(_.repoName == job.repoName).map(_.owningTeams).getOrElse(Seq.empty[TeamName])
                            job.latestBuild match
                              // 360+ days, all tests (any build status)
                              case Some(build) if build.timestamp.isBefore(oldBuildCutoff)
                                               => build.result.map: _ =>
                                                    InactiveTestRepo(job.repoName, s"${catalogueRepoLinkForSlack(job.repoName)} has a test job: <${job.jenkinsUrl}|${job.jobName}> that hasn't run in 360 days.", owningTeams)

                              // 90+ days, Acceptance tests (any build status)
                              case Some(build) if build.timestamp.isBefore(acceptanceCutoff) && job.testType.contains(TestType.Acceptance)
                                               => build.result.map: _ =>
                                                    InactiveTestRepo(job.repoName, s"${catalogueRepoLinkForSlack(job.repoName)} has a test job: <${job.jenkinsUrl}|${job.jobName}> that hasn't run in 90 days.", owningTeams)

                              // 30+ days, other tests (only FAILURE or UNSTABLE or ABORTED)
                              case Some(build) if build.timestamp.isBefore(failedBuildCutoff)
                                               && !job.testType.contains(TestType.Performance)
                                               && !job.testType.contains(TestType.Acceptance)
                                               => build.result
                                                    .filter: result =>
                                                      Set(BuildResult.Failure, BuildResult.Unstable, BuildResult.Aborted).contains(result)
                                                    .map: result =>
                                                      InactiveTestRepo(job.repoName, s"${catalogueRepoLinkForSlack(job.repoName)} has a test job: <${job.jenkinsUrl}|${job.jobName}> that hasn't run in 30 days.", owningTeams)
                              // No build record
                              case None        => Some(InactiveTestRepo(job.repoName, s"${catalogueRepoLinkForSlack(job.repoName)} has a test job: <${job.jenkinsUrl}|${job.jobName}> that has no build record.", owningTeams))
                              case _           => None
      reposWithNoJobs  =  allTestRepos
                            .filterNot(testRepo => allTestJobs.map(_.repoName).contains(testRepo.repoName))
                            .map: repo =>
                              InactiveTestRepo(repo.repoName, s"${catalogueRepoLinkForSlack(repo.repoName)} has no test job defined in Jenkins Github repositories.", repo.owningTeams)
      groupedByTeam    =  (reposWithOldJobs ++ reposWithNoJobs)
                            .map(repo => Map(TeamName("PlatOps") -> Seq(repo)))
                            .combineAll
      filteredCIP      =  groupedByTeam.filterNot:
                            (team, _) => team.asString.split("\\s+").contains("CIP")
      responses        <- filteredCIP.toList.foldLeftM(List.empty[(TeamName, SlackNotificationsConnector.Response)]):
                            (acc, inactiveTestRepos) =>
                              val (team, testRepos) = inactiveTestRepos
                              slackNotificationsConnector
                                .sendMessage(infoNotification(TeamName("PlatOps"), testRepos))
                                .map(resp => acc :+ (TeamName("PlatOps"), resp))
      _               =  responses.map:
                           case (team, rsp) if rsp.errors.nonEmpty => logger.warn(s"Sending Inactive Test Repository message to ${team.asString} had errors ${rsp.errors.mkString(" : ")}")
                           case (team, _)                          => logger.info(s"Successfully sent Inactive Test Repository message to ${team.asString}")
    yield ()

  private def infoNotification(teamName: TeamName, testRepos: Seq[InactiveTestRepo]): SlackNotificationsConnector.Request =
    val heading = SlackNotificationsConnector.mrkdwnBlock:
      "*Investigation Required!*"

    val msg = SlackNotificationsConnector.mrkdwnBlock:
      s"Hello ${teamName.asString}, the following test repositories may be inactive please review:"

    val warnings =
      testRepos
        .toList
        .sortBy(_.repoName.asString)
        .grouped(10)
        .map: batch =>
          SlackNotificationsConnector.mrkdwnBlock:
            batch
              .map(testRepo => s"• ${testRepo.message}")
              .mkString("\\n")
        .toSeq

    val link = SlackNotificationsConnector.mrkdwnBlock:
      s"To stay informed on your teams Test Results, visit <https://catalogue.tax.service.gov.uk/tests?teamName=${teamName.asString}|Test Results Page> in the Catalogue."

    SlackNotificationsConnector.Request(
      channelLookup   = SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName),
      displayName     = "MDTP Catalogue",
      emoji           = ":tudor-crown:",
      text            = "The test repositories may be inactive",
      blocks          = Seq(heading, msg) ++ warnings :+ link,
      callbackChannel = Some("team-platops-alerts")
    )

  private def catalogueRepoLinkForSlack(repoName: RepoName): String =
    s"https://catalogue.tax.service.gov.uk/repositories/$repoName|$repoName"

object InactiveTestRepoNotifierService:
  case class InactiveTestRepo(
    repoName   : RepoName
  , message    : String
  , owningTeams: Seq[TeamName]
  )
