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
