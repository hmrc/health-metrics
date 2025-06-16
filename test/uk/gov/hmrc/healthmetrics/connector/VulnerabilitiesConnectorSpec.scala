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
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, SlugInfoFlag, TeamName}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class VulnerabilitiesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.vulnerabilities.port" -> wireMockPort
    , "microservice.services.vulnerabilities.host" -> wireMockHost
    )
  )

  private val connector = VulnerabilitiesConnector(httpClientV2, servicesConfig)

  "VulnerabilitiesConnector.vulnerabilityCounts" should:
    "return latest vulnerability counts for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/vulnerabilities/api/reports/latest/counts?team=team1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "service": "repo-1",
                    "actionRequired": 0,
                    "noActionRequired": 2,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  },
                  {
                    "service": "repo-2",
                    "actionRequired": 1,
                    "noActionRequired": 2,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  },
                  {
                    "service": "repo-3",
                    "actionRequired": 2,
                    "noActionRequired": 1,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  }
                ]"""


      connector
        .vulnerabilityCounts(TeamName("team1"): MetricFilter, SlugInfoFlag.Latest)
        .futureValue shouldBe Seq(
          TotalVulnerabilityCount(actionRequired = 0)
        , TotalVulnerabilityCount(actionRequired = 1)
        , TotalVulnerabilityCount(actionRequired = 2)
        )

    "return latest vulnerability counts for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/vulnerabilities/api/reports/latest/counts?digitalService=digitalService1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "service": "repo-1",
                    "actionRequired": 0,
                    "noActionRequired": 2,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  },
                  {
                    "service": "repo-2",
                    "actionRequired": 1,
                    "noActionRequired": 2,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  },
                  {
                    "service": "repo-3",
                    "actionRequired": 2,
                    "noActionRequired": 1,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  }
                ]"""


      connector
        .vulnerabilityCounts(DigitalService("digitalService1"): MetricFilter, SlugInfoFlag.Latest)
        .futureValue shouldBe Seq(
          TotalVulnerabilityCount(actionRequired = 0)
        , TotalVulnerabilityCount(actionRequired = 1)
        , TotalVulnerabilityCount(actionRequired = 2)
        )

    "return production vulnerability counts for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/vulnerabilities/api/reports/production/counts?team=team1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "service": "repo-1",
                    "actionRequired": 0,
                    "noActionRequired": 2,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  },
                  {
                    "service": "repo-2",
                    "actionRequired": 1,
                    "noActionRequired": 2,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  },
                  {
                    "service": "repo-3",
                    "actionRequired": 2,
                    "noActionRequired": 1,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  }
                ]"""


      connector
        .vulnerabilityCounts(TeamName("team1"): MetricFilter, SlugInfoFlag.Production)
        .futureValue shouldBe Seq(
          TotalVulnerabilityCount(actionRequired = 0)
        , TotalVulnerabilityCount(actionRequired = 1)
        , TotalVulnerabilityCount(actionRequired = 2)
        )

    "return production vulnerability counts for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/vulnerabilities/api/reports/production/counts?digitalService=digitalService1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "service": "repo-1",
                    "actionRequired": 0,
                    "noActionRequired": 2,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  },
                  {
                    "service": "repo-2",
                    "actionRequired": 1,
                    "noActionRequired": 2,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  },
                  {
                    "service": "repo-3",
                    "actionRequired": 2,
                    "noActionRequired": 1,
                    "investigationOngoing": 0,
                    "uncurated": 0
                  }
                ]"""


      connector
        .vulnerabilityCounts(DigitalService("digitalService1"): MetricFilter, SlugInfoFlag.Production)
        .futureValue shouldBe Seq(
          TotalVulnerabilityCount(actionRequired = 0)
        , TotalVulnerabilityCount(actionRequired = 1)
        , TotalVulnerabilityCount(actionRequired = 2)
        )
