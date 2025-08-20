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
  import VulnerabilitiesConnector._

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

  "VulnerabilitiesConnector.getRepository" should:
    "correctly parse json response" in:
      val service = "Service_A"
      val version = "1.0.0"
      val flag    = "latest"
      stubFor:
        WireMock.get(urlEqualTo(s"/vulnerabilities/api/summaries?service=%22$service%22&version=$version&flag=$flag&curationStatus=ACTION_REQUIRED"))
          .willReturn(aResponse().withBodyFile("vulnerabilities/summaries.json"))

      connector.vulnerabilitySummaries(Some(service), Some(version), Some(flag)).futureValue shouldBe Seq(
        DistinctVulnerability(
          vulnerableComponentName    = "gav://g:a"
        , vulnerableComponentVersion = "1.0"
        , id                         = "CVE-1"
        , occurrences                = List(
            VulnerabilityOccurrence(
              "gav://g:a",
              "1.0",
              "Service_A-1.0.0/lib/g.a-1.0.jar",
              Seq(TeamName("team")),
              "service"
            )
          )
        ),
        DistinctVulnerability(
          vulnerableComponentName    = "gav://g:a"
        , vulnerableComponentVersion = "1.0"
        , id                         = "CVE-2"
        , occurrences                = List(
            VulnerabilityOccurrence(
              "gav://g:a",
              "1.0",
              "Service_A-1.0.0/lib/g.a-1.0.jar",
              Seq(TeamName("team")),
              "service"
            )
          )
        )
      )

  "DistinctVulnerability.matchesGav" should:
    "match gav" in:
      val vulnerability =
        DistinctVulnerability(
          vulnerableComponentName    = "gav://g:a"
        , vulnerableComponentVersion = "1.0"
        , id                         = "CVE-1"
        , occurrences                = List(
            VulnerabilityOccurrence(
              "gav://g:a",
              "1.0",
              "Service_A-1.0.0/lib/g.a-1.0.jar",
              Seq(TeamName("team")),
              "service"
            )
          )
        )

      vulnerability.matchesGav(group = "g", artefact = "a", version = "1.0", scalaVersion = None) shouldBe true

    "match component in path" in:
      val vulnerability =
        DistinctVulnerability(
          vulnerableComponentName    = "gav://g:a"
        , vulnerableComponentVersion = "1.0"
        , id                         = "CVE-1"
        , occurrences                = List(
            VulnerabilityOccurrence(
              "gav://g:a",
              "1.0",
              "Service_A-1.0.0/lib/g2.a2-1.0.jar/META-INF/maven/g/a/pom.xml",
              Seq(TeamName("team")),
              "service"
            )
          )
        )

      vulnerability.matchesGav(group = "g2", artefact = "a2", version = "1.0", scalaVersion = None) shouldBe true

    "match component in path (java WAR)" in:
      val vulnerability =
        DistinctVulnerability(
          vulnerableComponentName    = "gav://g:a"
        , vulnerableComponentVersion = "1.0"
        , id                         = "CVE-1"
        , occurrences                = List(
            VulnerabilityOccurrence(
              "gav://g:a",
              "1.0",
              "Service_A-1.0.0/lib/a2-1.0.jar/META-INF/maven/g/a/pom.xml",
              Seq(TeamName("team")),
              "service"
            )
          )
        )

      vulnerability.matchesGav(group = "g2", artefact = "a2", version = "1.0", scalaVersion = None) shouldBe true

    "match scala version in name" in:
      val vulnerability =
        DistinctVulnerability(
          vulnerableComponentName    = "gav://g:a_2.13"
        , vulnerableComponentVersion = "1.0"
        , id                         = "CVE-1"
        , occurrences                = List(
            VulnerabilityOccurrence(
              "gav://g:a_2.13",
              "1.0",
              "Service_A-1.0.0/lib/g2.a2-1.0.jar/META-INF/maven/g/a_2.13/pom.xml",
              Seq(TeamName("team")),
              "service"
            )
          )
        )

      vulnerability.matchesGav(group = "g", artefact = "a", version = "1.0", scalaVersion = Some("2.13")) shouldBe true

    "match scala version in path" in:
      val vulnerability =
        DistinctVulnerability(
          vulnerableComponentName    = "gav://g:a"
        , vulnerableComponentVersion = "1.0"
        , id                         = "CVE-1"
        , occurrences                = List(
            VulnerabilityOccurrence(
              "gav://g:a",
              "1.0",
              "Service_A-1.0.0/lib/g2.a2_2.13-1.0.jar/META-INF/maven/g/a/pom.xml",
              Seq(TeamName("team")),
              "service"
            )
          )
        )

      vulnerability.matchesGav(group = "g2", artefact = "a2", version = "1.0", scalaVersion = Some("2.13")) shouldBe true

    "match scala version in path (java WAR)" in:
      val vulnerability =
        DistinctVulnerability(
          vulnerableComponentName    = "gav://g:a"
        , vulnerableComponentVersion = "1.0"
        , id                         = "CVE-1"
        , occurrences                = List(
            VulnerabilityOccurrence(
              "gav://g:a",
              "1.0",
              ".extract/webapps/Service_A.war/WEB-INF/lib/a2_2.13-1.0.jar",
              Seq(TeamName("team")),
              "service"
            )
          )
        )

      vulnerability.matchesGav(group = "g2", artefact = "a2", version = "1.0", scalaVersion = Some("2.13")) shouldBe true
