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
import uk.gov.hmrc.healthmetrics.connector.ServiceMetricsConnector.ServiceMetric
import uk.gov.hmrc.healthmetrics.model.{DigitalService, Environment, LogMetricId, MetricFilter, TeamName}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Clock, Duration, Instant, ZoneId}
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class ServiceMetricsConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.service-metrics.port" -> wireMockPort
    , "microservice.services.service-metrics.host" -> wireMockHost
    )
  )

  private val logDuration    = Configuration("service-metrics.logDuration" -> "3.days")
  private val clock          = Clock.fixed(Instant.parse("2025-06-01T00:00:00.0Z"), ZoneId.of("UTC"))
  private val connector      = ServiceMetricsConnector(httpClientV2, servicesConfig, logDuration, clock)

  private val from: Instant  = Instant.now(clock).minus(logDuration.get[Duration]("service-metrics.logDuration").toMillis, ChronoUnit.MILLIS)

  "ServiceMetricsConnector.metrics" should:
    "return production service metrics for a team" in:
      stubFor:
        WireMock.get(urlEqualTo(s"/service-metrics/log-metrics?team=Team+1&environment=production&from=$from"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "service": "repo-1",
                    "id": "container-kills",
                    "environment": "production",
                    "kibanaLink": "http://logs.production.local...",
                    "logCount": 1
                  },
                  {
                    "service": "repo-2",
                    "id": "slow-running-query",
                    "environment": "production",
                    "kibanaLink": "http://logs.production.local...",
                    "logCount": 24
                  },
                  {
                    "service": "repo-3",
                    "id": "non-indexed-query",
                    "environment": "production",
                    "kibanaLink": "http://logs.production.local...",
                    "logCount": 2
                  }
                ]"""

      connector
        .metrics(TeamName("Team 1"): MetricFilter, Environment.Production)
        .futureValue shouldBe List(
          ServiceMetric("repo-1", LogMetricId.ContainerKills  , Environment.Production, "http://logs.production.local..." , 1 )
        , ServiceMetric("repo-2", LogMetricId.SlowRunningQuery, Environment.Production, "http://logs.production.local..." , 24)
        , ServiceMetric("repo-3", LogMetricId.NonIndexedQuery , Environment.Production, "http://logs.production.local..." , 2 )
        )

    "return production service metrics for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo(s"/service-metrics/log-metrics?digitalService=Digital+Service+1&environment=production&from=$from"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "service": "repo-1",
                    "id": "container-kills",
                    "environment": "production",
                    "kibanaLink": "http://logs.production.local...",
                    "logCount": 1
                  },
                  {
                    "service": "repo-2",
                    "id": "slow-running-query",
                    "environment": "production",
                    "kibanaLink": "http://logs.production.local...",
                    "logCount": 24
                  },
                  {
                    "service": "repo-3",
                    "id": "non-indexed-query",
                    "environment": "production",
                    "kibanaLink": "http://logs.production.local...",
                    "logCount": 2
                  }
                ]"""

      connector
        .metrics(DigitalService("Digital Service 1"): MetricFilter, Environment.Production)
        .futureValue shouldBe List(
          ServiceMetric("repo-1", LogMetricId.ContainerKills  , Environment.Production, "http://logs.production.local..." , 1 )
        , ServiceMetric("repo-2", LogMetricId.SlowRunningQuery, Environment.Production, "http://logs.production.local..." , 24)
        , ServiceMetric("repo-3", LogMetricId.NonIndexedQuery , Environment.Production, "http://logs.production.local..." , 2 )
        )
