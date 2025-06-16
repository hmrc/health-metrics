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

import play.api.Configuration
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.healthmetrics.connector.ServiceMetricsConnector.ServiceMetric
import uk.gov.hmrc.healthmetrics.model.{DigitalService, Environment, LogMetricId, MetricFilter, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Clock, Instant}
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

@Singleton
class ServiceMetricsConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
, configuration : Configuration
, clock         : Clock = Clock.systemDefaultZone()
)(using
  ec: ExecutionContext
):
  import HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("service-metrics")
    
  private val logDuration: Duration =
    configuration.get[Duration]("service-metrics.logDuration")

  def metrics(
    metricFilter: MetricFilter
  , environment : Environment
  )(using
    HeaderCarrier
  ): Future[Seq[ServiceMetric]] =
    given Reads[ServiceMetric] = ServiceMetric.reads

    val from = Instant.now(clock).minus(logDuration.toMillis, ChronoUnit.MILLIS)

    val params: MetricFilter => Map[String, String] =
      case TeamName(name)       => Map("team"           -> name)
      case DigitalService(name) => Map("digitalService" -> name)

    httpClientV2
      .get(url"$url/service-metrics/log-metrics?${params(metricFilter)}&environment=${environment.asString}&from=$from")
      .execute[Seq[ServiceMetric]]

object ServiceMetricsConnector:
  case class ServiceMetric(
    service    : String
  , id         : LogMetricId
  , environment: Environment
  , kibanaLink : String
  , logCount   : Int
  )

  object ServiceMetric:
    val reads: Reads[ServiceMetric] =
      ( (__ \ "service"     ).read[String]
      ~ (__ \ "id"          ).read[LogMetricId]
      ~ (__ \ "environment" ).read[Environment]
      ~ (__ \ "kibanaLink"  ).read[String]
      ~ (__ \ "logCount"    ).read[Int]
      )(ServiceMetric.apply)
