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
import uk.gov.hmrc.healthmetrics.connector.PlatformInitiativesConnector.{PlatformInitiative, Progress}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, TeamName}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class PlatformInitiativesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.platform-initiatives.port" -> wireMockPort
    , "microservice.services.platform-initiatives.host" -> wireMockHost
    )
  )

  private val connector = PlatformInitiativesConnector(httpClientV2, servicesConfig)

  "PlatformInitiativesConnector.getInitiatives" should:
    "return platform initiatives for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/platform-initiatives/initiatives?teamName=Team+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "initiativeName": "Tudor Crown Upgrade - Production",
                    "initiativeDescription": "Monitoring repos still using [play-frontend-hmrc]...",
                    "progress": {
                      "current": 1,
                      "target": 3
                    },
                    "completedLegend": "Completed",
                    "inProgressLegend": "Not Completed"
                  },
                  {
                    "initiativeName": "Scala 3 Upgrade",
                    "initiativeDescription": "Scala 3 upgrade [repos still using Scala 2.13 and below]...",
                    "progress": {
                      "current": 0,
                      "target": 7
                    },
                    "completedLegend": "Completed",
                    "inProgressLegend": "Not Completed"
                  },
                  {
                    "initiativeName": "Play 3.0 upgrade - Production",
                    "initiativeDescription": "Play 3.0 upgrade - Deprecate [Play 2.9 and below]...",
                    "progress": {
                      "current": 7,
                      "target": 7
                    },
                    "completedLegend": "Completed",
                    "inProgressLegend": "Not Completed"
                  }
                ]"""

      connector
        .getInitiatives(TeamName("Team 1"): MetricFilter)
        .futureValue shouldBe Seq(
          PlatformInitiative(Progress(current = 1, target = 3))
        , PlatformInitiative(Progress(current = 0, target = 7))
        , PlatformInitiative(Progress(current = 7, target = 7))
        )

    "return platform initiatives for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/platform-initiatives/initiatives?digitalService=Digital+Service+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "initiativeName": "Tudor Crown Upgrade - Production",
                    "initiativeDescription": "Monitoring repos still using [play-frontend-hmrc]...",
                    "progress": {
                      "current": 1,
                      "target": 3
                    },
                    "completedLegend": "Completed",
                    "inProgressLegend": "Not Completed"
                  },
                  {
                    "initiativeName": "Scala 3 Upgrade",
                    "initiativeDescription": "Scala 3 upgrade [repos still using Scala 2.13 and below]...",
                    "progress": {
                      "current": 0,
                      "target": 7
                    },
                    "completedLegend": "Completed",
                    "inProgressLegend": "Not Completed"
                  },
                  {
                    "initiativeName": "Play 3.0 upgrade - Production",
                    "initiativeDescription": "Play 3.0 upgrade - Deprecate [Play 2.9 and below]...",
                    "progress": {
                      "current": 7,
                      "target": 7
                    },
                    "completedLegend": "Completed",
                    "inProgressLegend": "Not Completed"
                  }
                ]"""

      connector
        .getInitiatives(DigitalService("Digital Service 1"): MetricFilter)
        .futureValue shouldBe Seq(
          PlatformInitiative(Progress(current = 1, target = 3))
        , PlatformInitiative(Progress(current = 0, target = 7))
        , PlatformInitiative(Progress(current = 7, target = 7))
        )
