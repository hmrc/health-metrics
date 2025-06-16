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
import uk.gov.hmrc.healthmetrics.connector.ShutterConnector.ShutterState
import uk.gov.hmrc.healthmetrics.model.{DigitalService, Environment, MetricFilter, ShutterStatusValue, ShutterType, TeamName}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class ShutterConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.shutter-api.port" -> wireMockPort
    , "microservice.services.shutter-api.host" -> wireMockHost
    )
  )

  private val connector = ShutterConnector(httpClientV2, servicesConfig)
  
  "ShutterConnector.getShutterStates" should:
    "return all frontend shutter states for a team in production" in:
      stubFor:
        WireMock.get(urlEqualTo("/shutter-api/production/frontend/states?teamName=Team+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "name": "service-1",
                    "environment": "production",
                    "type": "frontend",
                    "status": {
                      "value": "shuttered"
                    }
                  },
                  {
                    "name": "service-2",
                    "environment": "production",
                    "type": "frontend",
                    "status": {
                      "value": "unshuttered"
                    }
                  }
                ]"""

      connector
        .getShutterStates(ShutterType.Frontend, Environment.Production, TeamName("Team 1"): MetricFilter)
        .futureValue shouldBe Seq(
          ShutterState(ShutterType.Frontend, ShutterStatusValue.Shuttered  )
        , ShutterState(ShutterType.Frontend, ShutterStatusValue.Unshuttered)
        )

    "return all api shutter states for a team in production" in:
      stubFor:
        WireMock.get(urlEqualTo("/shutter-api/production/api/states?teamName=Team+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "name": "service-1",
                    "environment": "production",
                    "type": "api",
                    "status": {
                      "value": "shuttered"
                    }
                  },
                  {
                    "name": "service-2",
                    "environment": "production",
                    "type": "api",
                    "status": {
                      "value": "unshuttered"
                    }
                  }
                ]"""

      connector
        .getShutterStates(ShutterType.Api, Environment.Production, TeamName("Team 1"): MetricFilter)
        .futureValue shouldBe Seq(
          ShutterState(ShutterType.Api, ShutterStatusValue.Shuttered  )
        , ShutterState(ShutterType.Api, ShutterStatusValue.Unshuttered)
        )

    "return all frontend shutter states for a digital service in production" in:
      stubFor:
        WireMock.get(urlEqualTo("/shutter-api/production/frontend/states?digitalService=Digital+Service+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "name": "service-1",
                    "environment": "production",
                    "type": "frontend",
                    "status": {
                      "value": "shuttered"
                    }
                  },
                  {
                    "name": "service-2",
                    "environment": "production",
                    "type": "frontend",
                    "status": {
                      "value": "unshuttered"
                    }
                  }
                ]"""

      connector
        .getShutterStates(ShutterType.Frontend, Environment.Production, DigitalService("Digital Service 1"): MetricFilter)
        .futureValue shouldBe Seq(
          ShutterState(ShutterType.Frontend, ShutterStatusValue.Shuttered  )
        , ShutterState(ShutterType.Frontend, ShutterStatusValue.Unshuttered)
        )


    "return all api shutter states for a digital service in production" in:
      stubFor:
        WireMock.get(urlEqualTo("/shutter-api/production/api/states?digitalService=Digital+Service+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "name": "service-1",
                    "environment": "production",
                    "type": "api",
                    "status": {
                      "value": "shuttered"
                    }
                  },
                  {
                    "name": "service-2",
                    "environment": "production",
                    "type": "api",
                    "status": {
                      "value": "unshuttered"
                    }
                  }
                ]"""

      connector
        .getShutterStates(ShutterType.Api, Environment.Production, DigitalService("Digital Service 1"): MetricFilter)
        .futureValue shouldBe Seq(
          ShutterState(ShutterType.Api, ShutterStatusValue.Shuttered  )
        , ShutterState(ShutterType.Api, ShutterStatusValue.Unshuttered)
        )
