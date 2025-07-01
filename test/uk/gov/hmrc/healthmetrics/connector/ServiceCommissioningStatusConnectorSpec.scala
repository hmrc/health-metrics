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
import uk.gov.hmrc.healthmetrics.connector.ServiceCommissioningStatusConnector.{CachedServiceCheck, Warning}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, TeamName}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class ServiceCommissioningStatusConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.service-commissioning-status.port" -> wireMockPort
    , "microservice.services.service-commissioning-status.host" -> wireMockHost
    )
  )

  private val connector = ServiceCommissioningStatusConnector(httpClientV2, servicesConfig)

  "ServiceCommissioningStatusConnector.cachedCommissioningStatus" should:
    "return all service commissioning state warnings for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/service-commissioning-status/cached-status?teamName=Team+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody(cachedServiceCheckJson)

      connector
        .cachedCommissioningStatus(TeamName("Team 1"): MetricFilter)
        .futureValue shouldBe Seq(
          CachedServiceCheck(Some(Seq(
            Warning("App Config Environment Staging", "Service not deployed for 60 days in Staging and has remaining config.")
          , Warning("App Config Environment QA", "Service not deployed for 60 days in QA and has remaining config."          )
          )))
        , CachedServiceCheck(None)
        )

    "return all service commissioning state warnings for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/service-commissioning-status/cached-status?digitalService=Digital+Service+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody(cachedServiceCheckJson)

      connector
        .cachedCommissioningStatus(DigitalService("Digital Service 1"): MetricFilter)
        .futureValue shouldBe Seq(
          CachedServiceCheck(Some(Seq(
            Warning("App Config Environment Staging", "Service not deployed for 60 days in Staging and has remaining config.")
          , Warning("App Config Environment QA", "Service not deployed for 60 days in QA and has remaining config."          )
          )))
        , CachedServiceCheck(None)
        )


  private lazy val cachedServiceCheckJson: String =
    s"""[
      {
        "serviceName": "repo-1",
        "warnings": [
          {
            "title": "App Config Environment Staging",
            "message": "Service not deployed for 60 days in Staging and has remaining config."
          },
          {
            "title": "App Config Environment QA",
            "message": "Service not deployed for 60 days in QA and has remaining config."
          }
        ]
      },
      {
        "serviceName": "repo-2"
      }
    ]"""
