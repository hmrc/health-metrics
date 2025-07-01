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

package uk.gov.hmrc.healthmetrics.connector

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, stubFor, urlEqualTo}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.healthmetrics.connector.LeakDetectionConnector.LeakDetectionRepositorySummary
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, TeamName}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class LeakDetectionConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.leak-detection.port" -> wireMockPort
    , "microservice.services.leak-detection.host" -> wireMockHost
    )
  )

  private val connector = LeakDetectionConnector(httpClientV2, servicesConfig)
  
  "LeakDetectionConnector.leakDetectionRepoSummaries" should:
    "return leak detection repo summaries for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/repositories/summary?team=Team+1&excludeNonIssues=true&includeBranches=false"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "lastScannedAt": "2025-05-12T17:20:30.916Z",
                    "isArchived": false,
                    "repository": "repo-1",
                    "firstScannedAt": "2021-11-16T09:13:39.388Z",
                    "unresolvedCount": 1,
                    "warningCount": 2,
                    "excludedCount": 3
                  },
                  {
                    "lastScannedAt": "2025-05-12T14:15:37.835Z",
                    "isArchived": false,
                    "repository": "repo-2",
                    "firstScannedAt": "2021-10-13T08:00:26.108Z",
                    "unresolvedCount": 3,
                    "warningCount": 2,
                    "excludedCount": 1
                  }
                ]"""

      connector
        .leakDetectionRepoSummaries(TeamName("Team 1"): MetricFilter)
        .futureValue shouldBe Seq(
          LeakDetectionRepositorySummary(unresolvedCount = 1, warningCount = 2, excludedCount = 3)
        , LeakDetectionRepositorySummary(unresolvedCount = 3, warningCount = 2, excludedCount = 1)
        )

    "return leak detection repo summaries for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/repositories/summary?digitalService=Digital+Service+1&excludeNonIssues=true&includeBranches=false"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "lastScannedAt": "2025-05-12T17:20:30.916Z",
                    "isArchived": false,
                    "repository": "repo-1",
                    "firstScannedAt": "2021-11-16T09:13:39.388Z",
                    "unresolvedCount": 1,
                    "warningCount": 2,
                    "excludedCount": 3
                  },
                  {
                    "lastScannedAt": "2025-05-12T14:15:37.835Z",
                    "isArchived": false,
                    "repository": "repo-2",
                    "firstScannedAt": "2021-10-13T08:00:26.108Z",
                    "unresolvedCount": 3,
                    "warningCount": 2,
                    "excludedCount": 1
                  }
                ]"""

      connector
        .leakDetectionRepoSummaries(DigitalService("Digital Service 1"): MetricFilter)
        .futureValue shouldBe Seq(
          LeakDetectionRepositorySummary(unresolvedCount = 1, warningCount = 2, excludedCount = 3)
        , LeakDetectionRepositorySummary(unresolvedCount = 3, warningCount = 2, excludedCount = 1)
        )
