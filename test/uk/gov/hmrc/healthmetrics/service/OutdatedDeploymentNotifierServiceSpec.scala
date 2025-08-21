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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.healthmetrics.connector.ReleasesConnector.WhatsRunningWhere
import uk.gov.hmrc.healthmetrics.connector.ReleasesConnector.WhatsRunningWhere.Deployment
import uk.gov.hmrc.healthmetrics.connector.{ReleasesConnector, SlackNotificationsConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.healthmetrics.model.*
import uk.gov.hmrc.http.HeaderCarrier
import java.time.temporal.ChronoUnit.{DAYS, MONTHS}
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OutdatedDeploymentNotifierServiceSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with MockitoSugar:

  "OutdatedDeploymentNotifierService.notify" should:
    "notify teams when they have outdated versions deployed in an environment for longer than 7 days" in new Setup:
      val stagingDeployment = Deployment(Environment.Staging, Version("1.0.0"), eightDaysAgo)
      val qaDeployment = Deployment(Environment.QA, Version("0.9.0"), eightDaysAgo)
      val externalTestDeployment = Deployment(Environment.ExternalTest, Version("1.0.0"), eightDaysAgo)
      val productionDeployment = Deployment(Environment.ExternalTest, Version("1.0.0"), eightDaysAgo)

      when(releasesConnector.releases(any[Option[MetricFilter]])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
            WhatsRunningWhere(ServiceName("repo-1"), List(stagingDeployment, qaDeployment, externalTestDeployment, productionDeployment)) ::
            WhatsRunningWhere(ServiceName("repo-2"), List(stagingDeployment, qaDeployment, externalTestDeployment, productionDeployment.copy(version = Version("2.0.0")))) ::
            Nil
        ))

      when(teamsAndReposConnector.allTeams()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          TeamsAndRepositoriesConnector.GitHubTeam(TeamName("Team 1"), None, Seq("repo-1", "repo-2")) ::
            TeamsAndRepositoriesConnector.GitHubTeam(TeamName("Team 2"), None, Seq( "repo-2")) ::
          Nil
        ))

      when(slackNotificationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify(now).futureValue

      verify(slackNotificationsConnector, times(2)) // one message per team
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])

  "not notify teams when they have outdated versions deployed in an environment for less than 7 days" in new Setup:
    val stagingDeployment      = Deployment(Environment.Staging, Version("1.0.0"), yesterday)
    val qaDeployment           = Deployment(Environment.QA, Version("0.9.0"), yesterday)
    val externalTestDeployment = Deployment(Environment.ExternalTest, Version("1.0.0"), yesterday)
    val productionDeployment   = Deployment(Environment.ExternalTest, Version("1.0.0"), yesterday)

    when(releasesConnector.releases(any[Option[MetricFilter]])(using any[HeaderCarrier]))
      .thenReturn(Future.successful(
        WhatsRunningWhere(ServiceName("repo-1"), List(stagingDeployment, qaDeployment, externalTestDeployment, productionDeployment)) ::
          WhatsRunningWhere(ServiceName("repo-2"), List(stagingDeployment, qaDeployment, externalTestDeployment, productionDeployment.copy(version = Version("2.0.0")))) ::
          Nil
      ))

    when(teamsAndReposConnector.allTeams()(using any[HeaderCarrier]))
      .thenReturn(Future.successful(
        TeamsAndRepositoriesConnector.GitHubTeam(TeamName("Team 1"), None, Seq("repo-1", "repo-2")) ::
          TeamsAndRepositoriesConnector.GitHubTeam(TeamName("Team 2"), None, Seq("repo-2")) ::
          Nil
      ))

    when(slackNotificationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
      .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

    service.notify(now).futureValue

    verify(slackNotificationsConnector, times(0)) // no deployments older than 7 days
      .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])


  case class Setup():
    given HeaderCarrier = HeaderCarrier()

    val now: Instant = java.time.Instant.now()
    val eightDaysAgo: Instant = now.minus(8L, DAYS)
    val yesterday: Instant = now.minus(1L, DAYS)

    val mockConfiguration: Configuration =
      Configuration(
        "outdated-deployment-notifier.minimumDeploymentAge" -> "7.days"
      )

    val releasesConnector            = mock[ReleasesConnector]
    val slackNotificationsConnector  = mock[SlackNotificationsConnector]
    val teamsAndReposConnector       = mock[TeamsAndRepositoriesConnector]

    val service = new OutdatedDeploymentNotifierService(mockConfiguration, releasesConnector, slackNotificationsConnector, teamsAndReposConnector)