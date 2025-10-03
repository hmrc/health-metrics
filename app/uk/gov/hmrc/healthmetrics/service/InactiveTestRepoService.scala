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
import uk.gov.hmrc.healthmetrics.model.{RepoName, TeamName}
import uk.gov.hmrc.healthmetrics.model.InactiveTestRepo
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
      oldJobs          =  allTestJobs.flatMap: job =>
                            val owningTeams: Seq[TeamName] = allTestRepos.find(_.repoName == job.repoName).map(_.owningTeams).getOrElse(Seq.empty)
                            job.latestBuild match
                              // 360+ days, Performance tests (any build status)
                              case Some(build) if build.timestamp.isBefore(oldBuildCutoff) && job.testType.contains("Performance")
                                               => build.result.map: result =>
                                                    InactiveTestRepo(
                                                      job.repoName
                                                    , job.jobName
                                                    , job.jenkinsUrl
                                                    , "Performance"
                                                    , s"$result +360 days"
                                                    , build.timestamp.toString
                                                    , owningTeams
                                                    )
                              // 360+ days, all other tests
                              case Some(build) if build.timestamp.isBefore(oldBuildCutoff)
                                               => build.result.map: result =>
                                                   InactiveTestRepo(
                                                     job.repoName
                                                   , job.jobName
                                                   , job.jenkinsUrl
                                                   , job.testType.getOrElse("")
                                                   , s"$result +360 days"
                                                   , build.timestamp.toString
                                                   , owningTeams
                                                   )
                              // 90+ days, Acceptance tests (any build status)
                              case Some(build) if build.timestamp.isBefore(acceptanceCutoff) && job.testType == Some("Acceptance")
                                               => build.result.map: result =>
                                                   InactiveTestRepo(
                                                     job.repoName
                                                   , job.jobName
                                                   , job.jenkinsUrl
                                                   , "Acceptance"
                                                   , s"$result +90 days"
                                                   , build.timestamp.toString
                                                   , owningTeams
                                                   )
                              // 30+ days, other tests (only FAILURE or UNSTABLE)
                              case Some(build) if build.timestamp.isBefore(failedBuildCutoff) && job.testType != Some("Performance") && job.testType != Some("Acceptance")
                                               => build.result.filter(r => r == "FAILURE" || r == "UNSTABLE").map: result =>
                                                   InactiveTestRepo(
                                                     job.repoName
                                                   , job.jobName
                                                   , job.jenkinsUrl
                                                   , job.testType.getOrElse("")
                                                   , s"$result +30 days"
                                                   , build.timestamp.toString
                                                   , owningTeams
                                                   )
                              // No build record
                              case None        => Some(InactiveTestRepo(job.repoName, job.jobName, job.jenkinsUrl, job.testType.getOrElse(""), "NO RECORD", "", owningTeams))
                              case _           => None
      noJobs           =  allTestRepos.filterNot(repo => allTestJobs.map(_.repoName).contains(repo.repoName)).map(repo => InactiveTestRepo(repoName = repo.repoName, jobName = "", jenkinsUrl = "", testType = "", buildStatus = "NO JOB", timestamp = "", owningTeams = repo.owningTeams))
      abTestRepos      =  oldJobs ++ noJobs
      deletedServices  <- teamsAndRepositoriesConnector.deletedServices()
      archivedServices <- teamsAndRepositoriesConnector.archivedServices()
      services         =  (deletedServices ++ archivedServices).toSet
      withUnderTest    <- abTestRepos.foldLeftM(Seq.empty[InactiveTestRepo]): (acc, testRepo) =>
                            teamsAndRepositoriesConnector.servicesUnderTest(testRepo.repoName).map: s =>
                              acc :+ testRepo.copy(services = s.filter(services.contains))
      groupedByTeam    =  withUnderTest
                            .take(4)
                            .map(repo => Map(TeamName("PlatOps") -> Seq(repo)))
                            .combineAll
      responses        <- groupedByTeam.toList.foldLeftM(List.empty[(TeamName, SlackNotificationsConnector.Response)]):
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
      ":alarm: ACTION REQUIRED! :alarm:"

    val msg = SlackNotificationsConnector.mrkdwnBlock:
      s"Hello ${teamName.asString}, the following test repositories may be inactive:"

    val warnings =
      testRepos
        .toList
        .sortBy(_.repoName.asString)
        .grouped(10)
        .map: batch =>
          SlackNotificationsConnector.mrkdwnBlock:
            batch
              .map(testRepo => s"• ${testRepo.repoName} has a ${testRepo.testType} job: <${testRepo.jenkinsUrl}|${testRepo.jobName}> that needs review")
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
