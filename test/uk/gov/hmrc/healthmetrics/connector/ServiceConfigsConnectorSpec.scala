/*
 * Copyright 2023 HM Revenue & Customs
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


import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.healthmetrics.model.{Environment, ServiceName, Version}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class ServiceConfigsConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig:
    Configuration(
      "microservice.services.service-configs.host" -> wireMockHost,
      "microservice.services.service-configs.port" -> wireMockPort,
    )

  private val connector = ServiceConfigsConnector(httpClientV2, servicesConfig)
  import ServiceConfigsConnector._

  "Retrieving bobby rules" should:
    "correctly parse json response" in:
      stubFor:
        get(urlEqualTo(s"/service-configs/bobby/rules"))
          .willReturn(aResponse().withBodyFile(s"service-configs/bobby-rules.json"))

      val playFrontend = BobbyRules.BobbyRule(
        group          =  "uk.gov.hmrc"
      , artefact       = "play-frontend"
      , versionRange   = "(,99.99.99)"
      , reason         = "Post Play Frontend upgrade"
      , from           = LocalDate.of(2015, 11, 2)
      , exemptProjects = Nil
      )

      val sbtAutoBuild = BobbyRules.BobbyRule(
        group          = "uk.gov.hmrc"
      , artefact       = "sbt-auto-build"
      , versionRange   = "(,1.4.0)"
      , reason         = "Play 2.5 upgrade"
      , from           = LocalDate.of(2017, 5, 1)
      , exemptProjects = Nil
      )

      connector.getBobbyRules().futureValue shouldBe BobbyRules(
        plugins   = List(sbtAutoBuild)
      , libraries = List(playFrontend)
      )

  "ServiceConfigsConnector.appRoutes" should:
    "return all of the routes for a given service:version" in:
      stubFor:
        get(urlEqualTo("/service-configs/app-routes/test-service/0.1.0"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""{
                      "service": "test-service",
                      "version": "0.1.0",
                      "routes": [
                        {
                          "verb": "GET",
                          "path": "/test-service/hello-world",
                          "controller": "uk.gov.hmrc.testservice.controller.HelloWorldController",
                          "method": "helloWorld",
                          "parameters": [],
                          "modifiers": []
                        }
                      ],
                      "unevaluatedRoutes": [
                        {
                          "path": "",
                          "router": "health.Routes"
                        }
                      ]
                    }"""

      connector
        .appRoutes(ServiceName("test-service"), Version("0.1.0"))
        .futureValue shouldBe Some(
          AppRoutes(
            service = ServiceName("test-service"),
            version = Version("0.1.0"),
            paths   = Seq("/test-service/hello-world")
          )
        )

    "return None when 404" in:
      stubFor:
        get(urlEqualTo("/service-configs/app-routes/nonsense-service/0.1.0"))
          .willReturn:
            aResponse()
              .withStatus(404)

      connector
        .appRoutes(ServiceName("nonsense-service"), Version("0.1.0"))
        .futureValue shouldBe None

  "ServiceConfigsConnector.frontendRoutes" should:
    "return routes for service" in:
      stubFor:
        get(urlEqualTo("/service-configs/routes?serviceName=test-service&environment=production&routeType=frontend"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                      {
                        "serviceName": "test-service",
                        "path": "/test-service",
                        "isRegex": false,
                        "routeType": "frontend",
                        "environment": "production"
                      }
                    ]"""

      connector
        .frontendRoutes(ServiceName("test-service"), Environment.Production, routeType = "frontend")
        .futureValue shouldBe Seq(
          FrontendRoute(
            path    = "/test-service",
            isRegex = false
          )
        )
