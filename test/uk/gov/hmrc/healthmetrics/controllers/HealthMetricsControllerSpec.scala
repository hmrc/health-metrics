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

package uk.gov.hmrc.healthmetrics.controllers

class HealthMetricsControllerSpec


import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.{eq as eqTo, *}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, HealthMetric, HealthMetricTimelineCount, TeamName}
import uk.gov.hmrc.healthmetrics.persistence.TeamHealthMetricsRepository
import uk.gov.hmrc.healthmetrics.service.HealthMetricsService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ServiceStatusControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar:

  private val mockHealthMetricsService       : HealthMetricsService        = mock[HealthMetricsService]
  private val mockTeamHealthMetricsRepository: TeamHealthMetricsRepository = mock[TeamHealthMetricsRepository]

  private val teamMetrics: Map[HealthMetric, Int] =
    Map(
      HealthMetric.OpenPRRaisedByMembersOfTeam             -> 1
    , HealthMetric.OpenPRForOwnedRepos                     -> 1
    , HealthMetric.LeakDetectionSummaries                  -> 1
    , HealthMetric.ProductionBobbyErrors                   -> 1
    , HealthMetric.LatestBobbyErrors                       -> 1
    , HealthMetric.ProductionBobbyWarnings                 -> 1
    , HealthMetric.LatestBobbyWarnings                     -> 1
    , HealthMetric.FrontendShutterStates                   -> 1
    , HealthMetric.ApiShutterStates                        -> 1
    , HealthMetric.PlatformInitiatives                     -> 1
    , HealthMetric.ProductionActionRequiredVulnerabilities -> 1
    , HealthMetric.LatestActionRequiredVulnerabilities     -> 1
    , HealthMetric.ServiceCommissioningStateWarnings       -> 1
    , HealthMetric.ContainerKills                          -> 1
    , HealthMetric.NonIndexedQueries                       -> 1
    , HealthMetric.SlowRunningQueries                      -> 1
    , HealthMetric.OutdatedOrHotFixedProductionDeployments -> 1
    , HealthMetric.TestFailures                            -> 1
    , HealthMetric.AccessibilityAssessmentViolations       -> 1
    , HealthMetric.SecurityAssessmentAlerts                -> 1
    )

  private val digitalServiceMetrics: Map[HealthMetric, Int] =
    Map(
      HealthMetric.OpenPRForOwnedRepos                     -> 1
    , HealthMetric.LeakDetectionSummaries                  -> 1
    , HealthMetric.ProductionBobbyErrors                   -> 1
    , HealthMetric.LatestBobbyErrors                       -> 1
    , HealthMetric.ProductionBobbyWarnings                 -> 1
    , HealthMetric.LatestBobbyWarnings                     -> 1
    , HealthMetric.FrontendShutterStates                   -> 1
    , HealthMetric.ApiShutterStates                        -> 1
    , HealthMetric.PlatformInitiatives                     -> 1
    , HealthMetric.ProductionActionRequiredVulnerabilities -> 1
    , HealthMetric.LatestActionRequiredVulnerabilities     -> 1
    , HealthMetric.ServiceCommissioningStateWarnings       -> 1
    , HealthMetric.ContainerKills                          -> 1
    , HealthMetric.NonIndexedQueries                       -> 1
    , HealthMetric.SlowRunningQueries                      -> 1
    , HealthMetric.OutdatedOrHotFixedProductionDeployments -> 1
    , HealthMetric.TestFailures                            -> 1
    , HealthMetric.AccessibilityAssessmentViolations       -> 1
    , HealthMetric.SecurityAssessmentAlerts                -> 1
    )


  private val latestTeamMetricsJson =
    """{
      "metrics": {
        "LATEST_BOBBY_ERRORS": 1,
        "NON_INDEXED_QUERIES": 1,
        "OPEN_PR_FOR_OWNED_REPOS": 1,
        "API_SHUTTER_STATES": 1,
        "LEAK_DETECTION_SUMMARIES": 1,
        "SERVICE_COMMISSIONING_STATE_WARNINGS": 1,
        "ACCESSIBILITY_ASSESSMENT_VIOLATIONS": 1,
        "LATEST_ACTION_REQUIRED_VULNERABILITIES": 1,
        "FRONTEND_SHUTTER_STATES": 1,
        "PRODUCTION_ACTION_REQUIRED_VULNERABILITIES": 1,
        "OUTDATED_OR_HOT_FIXED_PRODUCTION_DEPLOYMENTS": 1,
        "TEST_FAILURES": 1,
        "SLOW_RUNNING_QUERIES": 1,
        "CONTAINER_KILLS": 1,
        "PRODUCTION_BOBBY_WARNINGS": 1,
        "OPEN_PR_RAISED_BY_MEMBERS_OF_TEAM": 1,
        "LATEST_BOBBY_WARNINGS": 1,
        "PLATFORM_INITIATIVES": 1,
        "PRODUCTION_BOBBY_ERRORS": 1,
        "SECURITY_ASSESSMENT_ALERTS": 1
      }
      }"""

  private val latestDigitalServiceHealthMetricsJson =
    """{
      "metrics": {
        "LATEST_BOBBY_ERRORS": 1,
        "NON_INDEXED_QUERIES": 1,
        "API_SHUTTER_STATES": 1,
        "LEAK_DETECTION_SUMMARIES": 1,
        "SERVICE_COMMISSIONING_STATE_WARNINGS": 1,
        "ACCESSIBILITY_ASSESSMENT_VIOLATIONS": 1,
        "LATEST_ACTION_REQUIRED_VULNERABILITIES": 1,
        "FRONTEND_SHUTTER_STATES": 1,
        "PRODUCTION_ACTION_REQUIRED_VULNERABILITIES": 1,
        "OUTDATED_OR_HOT_FIXED_PRODUCTION_DEPLOYMENTS": 1,
        "TEST_FAILURES": 1,
        "SLOW_RUNNING_QUERIES": 1,
        "CONTAINER_KILLS": 1,
        "PRODUCTION_BOBBY_WARNINGS": 1,
        "OPEN_PR_FOR_OWNED_REPOS": 1,
        "LATEST_BOBBY_WARNINGS": 1,
        "PLATFORM_INITIATIVES": 1,
        "PRODUCTION_BOBBY_ERRORS": 1,
        "SECURITY_ASSESSMENT_ALERTS": 1
      }
    }"""

  private val timelineCounts =
    """[
      {
        "date": "2025-06-12",
        "count": 1
      },
      {
        "date": "2025-06-13",
        "count": 1
      }
    ]"""


  "HealthMetricsController.latestTeamHealthMetrics" should:
    "return latest health metrics for a team as Json" in:

      val today = LocalDate.now()

      when(mockHealthMetricsService.generateHealthMetrics(eqTo(TeamName("Team 1")), eqTo(today))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(teamMetrics))

      val controller = HealthMetricsController(Helpers.stubControllerComponents(), mockHealthMetricsService, mockTeamHealthMetricsRepository)
      val result     = controller.latestTeamHealthMetrics(TeamName("Team 1"))(FakeRequest())
      val bodyText   = contentAsJson(result)
      bodyText mustBe Json.parse(latestTeamMetricsJson)

  "HealthMetricsController.latestDigitalServiceHealthMetrics" should:
    "return latest health metrics for a digital service as Json" in:

      val today = LocalDate.now()

      when(mockHealthMetricsService.generateHealthMetrics(eqTo(DigitalService("Digital Service 1")), eqTo(today))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(digitalServiceMetrics))

      val controller = HealthMetricsController(Helpers.stubControllerComponents(), mockHealthMetricsService, mockTeamHealthMetricsRepository)
      val result = controller.latestDigitalServiceHealthMetrics(DigitalService("Digital Service 1"))(FakeRequest())
      val bodyText = contentAsJson(result)
      bodyText mustBe Json.parse(latestDigitalServiceHealthMetricsJson)


  "HealthMetricsController.healthMetricsTimelineCounts" should:
    "return health metrics timeline counts as Json" in:

      val team = TeamName("Team 1")
      val from = LocalDate.parse("2025-06-12")
      val to   = LocalDate.parse("2025-06-14")

      when(mockTeamHealthMetricsRepository.getHealthMetricTimelineCounts(TeamName(eqTo("Team 1")), eqTo(HealthMetric.OpenPRForOwnedRepos), eqTo(from), eqTo(to)))
        .thenReturn(Future.successful(Seq(
          HealthMetricTimelineCount(LocalDate.parse("2025-06-12"), 1)
        , HealthMetricTimelineCount(LocalDate.parse("2025-06-13"), 1)
        )))

      val controller = HealthMetricsController(Helpers.stubControllerComponents(), mockHealthMetricsService, mockTeamHealthMetricsRepository)
      val result     = controller.healthMetricsTimelineCounts(team, HealthMetric.OpenPRForOwnedRepos, from = from, to = to)(FakeRequest())
      val bodyText   = contentAsJson(result)
      bodyText mustBe Json.parse(timelineCounts)
