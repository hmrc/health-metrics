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
import uk.gov.hmrc.healthmetrics.connector.ServiceDependenciesConnector.{BobbyReport, Violation}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, SlugInfoFlag, TeamName}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class ServiceDependenciesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.service-dependencies.port" -> wireMockPort
    , "microservice.services.service-dependencies.host" -> wireMockHost
    )
  )

  private val connector = ServiceDependenciesConnector(httpClientV2, servicesConfig)

  "ServiceDependenciesConnector.bobbyReports" should:
    "return the latest bobby reports for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/bobbyReports?team=Team+1&flag=latest"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1",
                    "repoVersion": "1.18.0",
                    "repoType": "Other",
                    "violations": [
                       {
                         "depGroup": "com.typesafe.play",
                         "depArtefact": "play",
                         "depVersion": "2.8.8",
                         "depScopes": [
                           "test",
                           "compile"
                         ],
                         "range": "(,2.9.0)",
                         "reason": "Play 3.0 upgrade - Deprecate [Play 2.8 and below]...",
                         "from": "2024-05-31",
                         "exempt": true
                       },
                       {
                         "depGroup": "com.typesafe.play",
                         "depArtefact": "play",
                         "depVersion": "2.8.8",
                         "depScopes": [
                           "test",
                           "compile"
                         ],
                         "range": "(,2.9.0)",
                         "reason": "Play 3.0 upgrade - Deprecate [Play 2.8 and below]...",
                         "from": "2025-05-31",
                         "exempt": false
                       }
                    ],
                    "lastUpdated": "2025-05-12T23:49:50.885Z",
                    "latest": true,
                    "production": false,
                    "qa": false,
                    "staging": false,
                    "development": false,
                    "externaltest": false,
                    "integration": false
                  },
                  {
                    "repoName": "repo-2",
                    "repoVersion": "1.177.0",
                    "repoType": "Other",
                    "violations": [],
                    "lastUpdated": "2025-05-12T23:50:26.330Z",
                    "latest": true,
                    "production": false,
                    "qa": false,
                    "staging": false,
                    "development": false,
                    "externaltest": false,
                    "integration": false
                  }
                ]"""

      connector
        .bobbyReports(TeamName("Team 1"): MetricFilter, SlugInfoFlag.Latest)
        .futureValue shouldBe Seq(
          BobbyReport(
            Seq(
              Violation(from = LocalDate.parse("2024-05-31"), exempt = true )
            , Violation(from = LocalDate.parse("2025-05-31"), exempt = false)
            )
          )
        , BobbyReport(Seq.empty[Violation])
        )

    "return production bobby reports for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/bobbyReports?team=Team+1&flag=production"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1",
                    "repoVersion": "1.18.0",
                    "repoType": "Other",
                    "violations": [
                       {
                         "depGroup": "com.typesafe.play",
                         "depArtefact": "play",
                         "depVersion": "2.8.8",
                         "depScopes": [
                           "test",
                           "compile"
                         ],
                         "range": "(,2.9.0)",
                         "reason": "Play 3.0 upgrade - Deprecate [Play 2.8 and below]...",
                         "from": "2024-05-31",
                         "exempt": true
                       },
                       {
                         "depGroup": "com.typesafe.play",
                         "depArtefact": "play",
                         "depVersion": "2.8.8",
                         "depScopes": [
                           "test",
                           "compile"
                         ],
                         "range": "(,2.9.0)",
                         "reason": "Play 3.0 upgrade - Deprecate [Play 2.8 and below]...",
                         "from": "2025-05-31",
                         "exempt": false
                       }
                    ],
                    "lastUpdated": "2025-05-12T23:49:50.885Z",
                    "latest": false,
                    "production": true,
                    "qa": false,
                    "staging": false,
                    "development": false,
                    "externaltest": false,
                    "integration": false
                  },
                  {
                    "repoName": "repo-2",
                    "repoVersion": "1.177.0",
                    "repoType": "Other",
                    "violations": [],
                    "lastUpdated": "2025-05-12T23:50:26.330Z",
                    "latest": false,
                    "production": true,
                    "qa": false,
                    "staging": false,
                    "development": false,
                    "externaltest": false,
                    "integration": false
                  }
                ]"""

      connector
        .bobbyReports(TeamName("Team 1"): MetricFilter, SlugInfoFlag.Production)
        .futureValue shouldBe Seq(
          BobbyReport(
            Seq(
              Violation(from = LocalDate.parse("2024-05-31"), exempt = true )
            , Violation(from = LocalDate.parse("2025-05-31"), exempt = false)
            )
          )
        , BobbyReport(Seq.empty[Violation])
        )

    "return the latest bobby reports for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/bobbyReports?digitalService=Digital+Service+1&flag=latest"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1",
                    "repoVersion": "1.18.0",
                    "repoType": "Other",
                    "violations": [
                       {
                         "depGroup": "com.typesafe.play",
                         "depArtefact": "play",
                         "depVersion": "2.8.8",
                         "depScopes": [
                           "test",
                           "compile"
                         ],
                         "range": "(,2.9.0)",
                         "reason": "Play 3.0 upgrade - Deprecate [Play 2.8 and below]...",
                         "from": "2024-05-31",
                         "exempt": true
                       },
                       {
                         "depGroup": "com.typesafe.play",
                         "depArtefact": "play",
                         "depVersion": "2.8.8",
                         "depScopes": [
                           "test",
                           "compile"
                         ],
                         "range": "(,2.9.0)",
                         "reason": "Play 3.0 upgrade - Deprecate [Play 2.8 and below]...",
                         "from": "2025-05-31",
                         "exempt": false
                       }
                    ],
                    "lastUpdated": "2025-05-12T23:49:50.885Z",
                    "latest": true,
                    "production": false,
                    "qa": false,
                    "staging": false,
                    "development": false,
                    "externaltest": false,
                    "integration": false
                  },
                  {
                    "repoName": "repo-2",
                    "repoVersion": "1.177.0",
                    "repoType": "Other",
                    "violations": [],
                    "lastUpdated": "2025-05-12T23:50:26.330Z",
                    "latest": true,
                    "production": false,
                    "qa": false,
                    "staging": false,
                    "development": false,
                    "externaltest": false,
                    "integration": false
                  }
                ]"""

      connector
        .bobbyReports(DigitalService("Digital Service 1"): MetricFilter, SlugInfoFlag.Latest)
        .futureValue shouldBe Seq(
          BobbyReport(
            Seq(
              Violation(from = LocalDate.parse("2024-05-31"), exempt = true )
            , Violation(from = LocalDate.parse("2025-05-31"), exempt = false)
            )
          )
        , BobbyReport(Seq.empty[Violation])
        )
      
    "return production bobby reports for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/bobbyReports?digitalService=Digital+Service+1&flag=production"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1",
                    "repoVersion": "1.18.0",
                    "repoType": "Other",
                    "violations": [
                       {
                         "depGroup": "com.typesafe.play",
                         "depArtefact": "play",
                         "depVersion": "2.8.8",
                         "depScopes": [
                           "test",
                           "compile"
                         ],
                         "range": "(,2.9.0)",
                         "reason": "Play 3.0 upgrade - Deprecate [Play 2.8 and below]...",
                         "from": "2024-05-31",
                         "exempt": true
                       },
                       {
                         "depGroup": "com.typesafe.play",
                         "depArtefact": "play",
                         "depVersion": "2.8.8",
                         "depScopes": [
                           "test",
                           "compile"
                         ],
                         "range": "(,2.9.0)",
                         "reason": "Play 3.0 upgrade - Deprecate [Play 2.8 and below]...",
                         "from": "2025-05-31",
                         "exempt": false
                       }
                    ],
                    "lastUpdated": "2025-05-12T23:49:50.885Z",
                    "latest": false,
                    "production": true,
                    "qa": false,
                    "staging": false,
                    "development": false,
                    "externaltest": false,
                    "integration": false
                  },
                  {
                    "repoName": "repo-2",
                    "repoVersion": "1.177.0",
                    "repoType": "Other",
                    "violations": [],
                    "lastUpdated": "2025-05-12T23:50:26.330Z",
                    "latest": false,
                    "production": true,
                    "qa": false,
                    "staging": false,
                    "development": false,
                    "externaltest": false,
                    "integration": false
                  }
                ]"""

      connector
        .bobbyReports(DigitalService("Digital Service 1"): MetricFilter, SlugInfoFlag.Production)
        .futureValue shouldBe Seq(
          BobbyReport(
            Seq(
              Violation(from = LocalDate.parse("2024-05-31"), exempt = true )
            , Violation(from = LocalDate.parse("2025-05-31"), exempt = false)
            )
          )
        , BobbyReport(Seq.empty[Violation])
        )
