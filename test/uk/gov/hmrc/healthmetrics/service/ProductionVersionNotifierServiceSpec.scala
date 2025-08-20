/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.healthmetrics.connector.SlackNotificationsConnector
import uk.gov.hmrc.healthmetrics.model.*

import java.time.temporal.ChronoUnit.{DAYS, MONTHS}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*
import uk.gov.hmrc.healthmetrics.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.healthmetrics.connector.ReleasesConnector

class ProductionVersionNotifierServiceSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with MockitoSugar:

  "The ProductionVersionNotifierService" should:

    "send notifications grouped by team" in new Setup:

      when(mockTeamsAndReposConnector.allTeams()(using any[HeaderCarrier]))
        .thenReturn(Future.successful((Seq(team1))))

      val releases: Seq[ReleasesConnector.WhatsRunningWhere] =
        ReleasesConnector.WhatsRunningWhere(
          serviceName = ServiceName("service1")
        , deployments  = ReleasesConnector.WhatsRunningWhere.Deployment(Environment.QA        , Version("1.1.0"), eightDaysAgo) ::
                         ReleasesConnector.WhatsRunningWhere.Deployment(Environment.Production, Version("1.0.0"), oneMonthAgo ) ::
                         Nil
        ) :: ReleasesConnector.WhatsRunningWhere(
          serviceName = ServiceName("service2")
        , deployments  = ReleasesConnector.WhatsRunningWhere.Deployment(Environment.QA        , Version("1.1.0"), eightDaysAgo) ::
                         ReleasesConnector.WhatsRunningWhere.Deployment(Environment.Production, Version("1.0.0"), yesterday   ) ::
                         Nil
        ) :: Nil

      when(mockReleasesConnector.releases(any())(using any[HeaderCarrier]))
        .thenReturn(Future.successful(releases))

      val captor: ArgumentCaptor[SlackNotificationsConnector.Request] =
        ArgumentCaptor.forClass(classOf[SlackNotificationsConnector.Request])

      when(mockSlackNotificationsConnector.sendMessage(captor.capture())(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      underTest.notify(now).futureValue

      captor.getAllValues.asScala.toSet shouldBe Set(
        underTest.lowerVersionInProduction(TeamName("team1"), List(ServiceName("service1")), 7.days)
      )

trait Setup extends MockitoSugar:

  val hc: HeaderCarrier = HeaderCarrier()

  val team1 = TeamsAndRepositoriesConnector.GitHubTeam(
    name           = TeamName("team1")
  , lastActiveDate = None
  , repos          = Seq("service1", "service2")
  )

  val nowAsLocalDateTime: LocalDateTime = LocalDateTime.parse("2023-09-22T10:00:00")

  val now         : Instant = nowAsLocalDateTime                  .toInstant(ZoneOffset.UTC)
  val oneMonthAgo : Instant = nowAsLocalDateTime.minus(1L, MONTHS).toInstant(ZoneOffset.UTC)
  val eightDaysAgo: Instant = nowAsLocalDateTime.minus(8L, DAYS  ).toInstant(ZoneOffset.UTC)
  val yesterday   : Instant = nowAsLocalDateTime.minus(1L, DAYS  ).toInstant(ZoneOffset.UTC)

  val mockConfiguration: Configuration =
    Configuration(
      "production-version-slack-notifier.minimumDeploymentAge" -> "7.days"
    )

  val mockReleasesConnector          : ReleasesConnector             = mock[ReleasesConnector]
  val mockSlackNotificationsConnector: SlackNotificationsConnector   = mock[SlackNotificationsConnector]
  val mockTeamsAndReposConnector     : TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]

  val underTest: ProductionVersionNotifierService =
    ProductionVersionNotifierService(
      mockConfiguration,
      mockReleasesConnector,
      mockSlackNotificationsConnector,
      mockTeamsAndReposConnector,
    )
