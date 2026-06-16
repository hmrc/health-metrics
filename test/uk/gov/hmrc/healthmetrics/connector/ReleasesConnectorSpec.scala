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
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.healthmetrics.model.{DigitalService, Environment, ServiceName, TeamName, Version}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class ReleasesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with IntegrationPatience
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig:
    Configuration(
      "microservice.services.releases-api.port" -> wireMockPort
    , "microservice.services.releases-api.host" -> wireMockHost
    )

  private val connector = ReleasesConnector(httpClientV2, servicesConfig)
  import ReleasesConnector._

  "ReleasesConnector.releases" should:

    val expectedReleases =
      WhatsRunningWhere(
        ServiceName("application-1")
      , WhatsRunningWhere.Deployment(Environment.QA        , Version(major = 1, minor = 138, patch = 0, original = "1.138.0"), lastDeployed = Instant.parse("2025-05-07T13:46:42.917Z")) ::
        WhatsRunningWhere.Deployment(Environment.Staging   , Version(major = 1, minor = 136, patch = 0, original = "1.136.0"), lastDeployed = Instant.parse("2025-04-17T14:28:46.210Z")) ::
        WhatsRunningWhere.Deployment(Environment.Production, Version(major = 1, minor = 136, patch = 0, original = "1.136.0"), lastDeployed = Instant.parse("2025-04-28T10:02:45.739Z")) ::
        Nil
      ) :: WhatsRunningWhere(
        ServiceName("application-2")
      , WhatsRunningWhere.Deployment(Environment.QA        , Version(major = 0, minor = 43, patch = 0, original = "0.43.0"), lastDeployed = Instant.parse("2025-05-07T13:05:14.832Z")) ::
        WhatsRunningWhere.Deployment(Environment.Staging   , Version(major = 0, minor = 43, patch = 0, original = "0.43.0"), lastDeployed = Instant.parse("2025-05-07T13:10:51.161Z")) ::
        WhatsRunningWhere.Deployment(Environment.Production, Version(major = 0, minor = 38, patch = 0, original = "0.38.0"), lastDeployed = Instant.parse("2025-04-28T11:11:53.646Z")) ::
        Nil
      ) :: Nil

    "return all releases" in:
      stubFor:
        WireMock.get(urlEqualTo("/releases-api/whats-running-where"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody(whatsRunningWhereJson)

      connector
        .releases()
        .futureValue
        .shouldBe(expectedReleases)

    "return all releases for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/releases-api/whats-running-where?teamName=Team+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody(whatsRunningWhereJson)

      connector
        .releases(Some(TeamName("Team 1")))
        .futureValue
        .shouldBe(expectedReleases)

    "return all releases for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/releases-api/whats-running-where?digitalService=Digital+Service+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody(whatsRunningWhereJson)

      connector
        .releases(Some(DigitalService("Digital Service 1")))
        .futureValue
        .shouldBe(expectedReleases)

  private val whatsRunningWhereJson: String =
    """[
      {
        "applicationName": "application-1",
        "versions": [
          {
            "environment": "qa",
            "versionNumber": "1.138.0",
            "lastDeployed": "2025-05-07T13:46:42.917Z",
            "deploymentId": "deployment-1",
            "config": [
              {
                "repoName": "app-config-qa",
                "fileName": "application-1.yaml",
                "commitId": "commit-1"
              },
              {
                "repoName": "app-config-common",
                "fileName": "qa-microservice-common.yaml",
                "commitId": "commit-2"
              },
              {
                "repoName": "app-config-base",
                "fileName": "application-1.conf",
                "commitId": "commit-3"
              }
            ]
          },
          {
            "environment": "staging",
            "versionNumber": "1.136.0",
            "lastDeployed": "2025-04-17T14:28:46.210Z",
            "deploymentId": "deployment-1",
            "config": [
              {
                "repoName": "app-config-staging",
                "fileName": "application-1.yaml",
                "commitId": "commit-1"
              },
              {
                "repoName": "app-config-common",
                "fileName": "staging-microservice-common.yaml",
                "commitId": "commit-2"
              },
              {
                "repoName": "app-config-base",
                "fileName": "application-1.conf",
                "commitId": "commit-3"
              }
            ]
          },
          {
            "environment": "production",
            "versionNumber": "1.136.0",
            "lastDeployed": "2025-04-28T10:02:45.739Z",
            "deploymentId": "deployment-1",
            "config": [
              {
                "repoName": "app-config-production",
                "fileName": "application-1.yaml",
                "commitId": "commit-1"
              },
              {
                "repoName": "app-config-common",
                "fileName": "production-microservice-common.yaml",
                "commitId": "commit-2"
              },
              {
                "repoName": "app-config-base",
                "fileName": "application-1.conf",
                "commitId": "commit-3"
              }
            ]
          }
        ]
      },
      {
        "applicationName": "application-2",
        "versions": [
          {
            "environment": "qa",
            "versionNumber": "0.43.0",
            "lastDeployed": "2025-05-07T13:05:14.832Z",
            "deploymentId": "deployment-1",
            "config": [
              {
                "repoName": "app-config-qa",
                "fileName": "application-2.yaml",
                "commitId": "commit-1"
              },
              {
                "repoName": "app-config-common",
                "fileName": "qa-frontend-common.yaml",
                "commitId": "commit-2"
              },
              {
                "repoName": "app-config-base",
                "fileName": "application-2.conf",
                "commitId": "commit-3"
              }
            ]
          },
          {
            "environment": "staging",
            "versionNumber": "0.43.0",
            "lastDeployed": "2025-05-07T13:10:51.161Z",
            "deploymentId": "deployment-1",
            "config": [
              {
                "repoName": "app-config-staging",
                "fileName": "application-2.yaml",
                "commitId": "commit-1"
              },
              {
                "repoName": "app-config-common",
                "fileName": "staging-frontend-common.yaml",
                "commitId": "commit-2"
              },
              {
                "repoName": "app-config-base",
                "fileName": "application-2.conf",
                "commitId": "commit-3"
              }
            ]
          },
          {
            "environment": "production",
            "versionNumber": "0.38.0",
            "lastDeployed": "2025-04-28T11:11:53.646Z",
            "deploymentId": "deployment-1",
            "config": [
              {
                "repoName": "app-config-production",
                "fileName": "application-2.yaml",
                "commitId": "commit-1"
              },
              {
                "repoName": "app-config-common",
                "fileName": "production-frontend-common.yaml",
                "commitId": "commit-2"
              },
              {
                "repoName": "app-config-base",
                "fileName": "application-2.conf",
                "commitId": "commit-3"
              }
            ]
          }
        ]
      }
    ]"""
