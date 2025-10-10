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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.healthmetrics.connector.{SlackNotificationsConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.healthmetrics.connector.TeamsAndRepositoriesConnector.{BuildData, BuildResult, JenkinsJob, Repo, TestType}
import uk.gov.hmrc.healthmetrics.model.{RepoName, TeamName}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class InactiveTestRepoNotifierServiceSpec
  extends AnyWordSpec
    with  Matchers
    with  ScalaFutures
    with  IntegrationPatience
    with  MockitoSugar:

  "InactiveTestRepoNotifierService.notify" should:
    "notify teams when test repositories have jobs that haven’t run in 360 days" in new Setup:
      when(teamsAndReposConnector.allTestRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Repo(RepoName("repo-1"), Seq.empty, None, false, Seq(TeamName("Team 1"), TeamName("Team 2"))) ::
          Repo(RepoName("repo-2"), Seq.empty, None, false, Seq(TeamName("Team 1"), TeamName("Team 2"))) ::
          Nil
        ))

      when(teamsAndReposConnector.allTestJobs()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          JenkinsJob(RepoName("repo-1"), "repo-1-performance-tests", "job-url", "test", Some(TestType.Performance), Some(BuildData(Some(BuildResult.Success), None, nowMinus361Days))) ::
          JenkinsJob(RepoName("repo-1"), "repo-1-acceptance-tests" , "job-url", "test", Some(TestType.Acceptance), Some(BuildData(Some(BuildResult.Success) , None, nowMinus361Days))) ::
          JenkinsJob(RepoName("repo-1"), "repo-1-tests"            , "job-url", "test", Some(TestType.Other)     , Some(BuildData(Some(BuildResult.Success) , None, now            ))) ::
          Nil
        ))

      when(slackNotifcationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify(now).futureValue

      verify(slackNotifcationsConnector, times(2)) // one message per team
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])

    "notify teams when test repositories have acceptance test jobs that haven’t run in 90 days" in new Setup:
      when(teamsAndReposConnector.allTestRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Repo(RepoName("repo-1"), Seq.empty, None, false, Seq(TeamName("Team 1"))) ::
          Repo(RepoName("repo-2"), Seq.empty, None, false, Seq(TeamName("Team 1"))) ::
          Nil
        ))

      when(teamsAndReposConnector.allTestJobs()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          JenkinsJob(RepoName("repo-1"), "repo-1-acceptance-tests" , "job-url", "test", Some(TestType.Acceptance), Some(BuildData(Some(BuildResult.Success) , None, nowMinus91Days))) ::
          JenkinsJob(RepoName("repo-2"), "repo-2-acceptance-tests" , "job-url", "test", Some(TestType.Acceptance), Some(BuildData(Some(BuildResult.Success) , None, now           ))) ::
          Nil
        ))

      when(slackNotifcationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify(now).futureValue

      verify(slackNotifcationsConnector, times(1)) // one message per team
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])

    "notify teams when test repositories have test jobs that are not performance or acceptance, are not successful, and haven’t run in 30 days" in new Setup:
      when(teamsAndReposConnector.allTestRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Repo(RepoName("repo-1"), Seq.empty, None, false, Seq(TeamName("Team 1"))) ::
          Repo(RepoName("repo-2"), Seq.empty, None, false, Seq(TeamName("Team 2"))) ::
          Repo(RepoName("repo-3"), Seq.empty, None, false, Seq(TeamName("Team 3"))) ::
          Repo(RepoName("repo-4"), Seq.empty, None, false, Seq(TeamName("Team 4"))) ::
          Nil
        ))

      when(teamsAndReposConnector.allTestJobs()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          JenkinsJob(RepoName("repo-1"), "repo-1-other-tests"   , "job-url", "test", Some(TestType.Other)   , Some(BuildData(Some(BuildResult.Failure) , None, nowMinus31Days))) ::
          JenkinsJob(RepoName("repo-2"), "repo-2-contract-tests", "job-url", "test", Some(TestType.Contract), Some(BuildData(Some(BuildResult.Aborted) , None, nowMinus31Days))) ::
          JenkinsJob(RepoName("repo-3"), "repo-3-other-tests"   , "job-url", "test", Some(TestType.Other)   , Some(BuildData(Some(BuildResult.Unstable), None, nowMinus31Days))) ::
          JenkinsJob(RepoName("repo-4"), "repo-4-contract-tests", "job-url", "test", Some(TestType.Contract), Some(BuildData(Some(BuildResult.Failure) , None, nowMinus31Days))) ::
          Nil
        ))

      when(slackNotifcationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify(now).futureValue

      verify(slackNotifcationsConnector, times(4)) // one message per team
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])

    "notify teams when test repositories have jobs but no build record" in new Setup:
      when(teamsAndReposConnector.allTestRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Repo(RepoName("repo-1"), Seq.empty, None, false, Seq(TeamName("Team 1"), TeamName("Team 2"))) ::
          Repo(RepoName("repo-2"), Seq.empty, None, false, Seq(TeamName("Team 3"), TeamName("Team 4"))) ::
          Nil
        ))

      when(teamsAndReposConnector.allTestJobs()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          JenkinsJob(RepoName("repo-1"), "repo-1-other-tests"      , "job-url", "test", Some(TestType.Other)      , None) ::
          JenkinsJob(RepoName("repo-1"), "repo-1-contract-tests"   , "job-url", "test", Some(TestType.Contract)   , None) ::
          JenkinsJob(RepoName("repo-2"), "repo-2-performance-tests", "job-url", "test", Some(TestType.Performance), None) ::
          JenkinsJob(RepoName("repo-2"), "repo-2-acceptance-tests" , "job-url", "test", Some(TestType.Acceptance) , None) ::
          Nil
        ))

      when(slackNotifcationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify(now).futureValue

      verify(slackNotifcationsConnector, times(4)) // one message per team
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])

    "notify teams when test repositories have no jobs defined" in new Setup:
      when(teamsAndReposConnector.allTestRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Repo(RepoName("repo-1"), Seq.empty, None, false, Seq(TeamName("Team 1"), TeamName("Team 2"))) ::
          Repo(RepoName("repo-2"), Seq.empty, None, false, Seq(TeamName("Team 3"), TeamName("Team 4"))) ::
          Nil
        ))

      when(teamsAndReposConnector.allTestJobs()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty[JenkinsJob]))

      when(slackNotifcationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify(now).futureValue

      verify(slackNotifcationsConnector, times(4)) // one message per team
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])
    
    "not notify teams for potentially inactive test repositories unless they meet the inactive repositories criteria" in new Setup:
      
      when(teamsAndReposConnector.allTestRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Repo(RepoName("repo-1"), Seq.empty, None, false, Seq(TeamName("Team 1"), TeamName("Team 2"))) ::
          Repo(RepoName("repo-2"), Seq.empty, None, false, Seq(TeamName("Team 1"), TeamName("Team 2"))) ::
          Nil
        ))

      when(teamsAndReposConnector.allTestJobs()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          JenkinsJob(RepoName("repo-1"), "repo-1-acceptance-tests" , "job-url", "test", Some(TestType.Acceptance) , Some(BuildData(Some(BuildResult.Success) , None, now))           ) ::
          JenkinsJob(RepoName("repo-1"), "repo-1-performance-tests", "job-url", "test", Some(TestType.Performance), Some(BuildData(Some(BuildResult.Success) , None, now))           ) ::
          JenkinsJob(RepoName("repo-2"), "repo-2-other-tests"      , "job-url", "test", Some(TestType.Other)      , Some(BuildData(Some(BuildResult.Success) , None, nowMinus91Days))) ::
          JenkinsJob(RepoName("repo-2"), "repo-2-acceptance-tests" , "job-url", "test", Some(TestType.Acceptance) , Some(BuildData(Some(BuildResult.Success) , None, nowMinus31Days))) ::
          JenkinsJob(RepoName("repo-2"), "repo-2-performance-tests", "job-url", "test", Some(TestType.Performance), Some(BuildData(Some(BuildResult.Success) , None, nowMinus91Days))) ::
          Nil
        ))

      when(slackNotifcationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify(now).futureValue

      verify(slackNotifcationsConnector, times(0))
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])

  case class Setup():
    given HeaderCarrier = HeaderCarrier()

    val now             = Instant.now()
    val nowMinus361Days = now.minus(361L, DAYS)
    val nowMinus91Days  = now.minus(91L , DAYS)
    val nowMinus31Days  = now.minus(31L , DAYS)

    val mockConfiguration =
      Configuration(
        "inactive-test-repositories-jobs.oldBuildCutoff"    -> "360.days"
      , "inactive-test-repositories-jobs.acceptanceCutoff"  -> "90.days"
      , "inactive-test-repositories-jobs.failedBuildCutoff" -> "30.days"
      )

    val teamsAndReposConnector     = mock[TeamsAndRepositoriesConnector]
    val slackNotifcationsConnector = mock[SlackNotificationsConnector]

    val service =  new InactiveTestRepoNotifierService(teamsAndReposConnector, slackNotifcationsConnector, mockConfiguration)


