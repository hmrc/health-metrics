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
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.healthmetrics.connector.ServiceMetricsConnector.ServiceMetric
import uk.gov.hmrc.healthmetrics.model.{DigitalService, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

@Singleton
class ServiceMetricsConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
, configuration : Configuration
)(using
  ec: ExecutionContext
):
  import HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("service-metrics")
    
  private val logDuration: Duration =
    configuration.get[Duration]("service-metrics.logDuration")
  
  
  def metrics(
    environment   : Option[String]    = None
  , teamName      : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  )(using HeaderCarrier): Future[Seq[ServiceMetric]] =
    given Reads[ServiceMetric] = ServiceMetric.reads
    val from = Instant.now().minus(logDuration.toMillis, ChronoUnit.MILLIS)
    httpClientV2
      .get(url"$url/service-metrics/log-metrics?team=${teamName.map(_.asString)}&digitalService=${digitalService.map(_.asString)}&environment=${environment}&from=$from")
      .execute[Seq[ServiceMetric]]

object ServiceMetricsConnector:
  case class ServiceMetric(
    service    : String
  , id         : String
  , environment: String
  , kibanaLink : String
  , logCount   : Int
  )

  object ServiceMetric:
    val reads: Reads[ServiceMetric] =
      ( (__ \ "service"     ).read[String]
      ~ (__ \ "id"          ).read[String]
      ~ (__ \ "environment" ).read[String]
      ~ (__ \ "kibanaLink"  ).read[String]
      ~ (__ \ "logCount"    ).read[Int]
      )(ServiceMetric.apply)

//given Parser[LogMetricId] = Parser.parser(LogMetricId.values)

//enum LogMetricId(
//  override val asString: String,
//  val displayString    : String
//) extends FromString
//  derives Reads, FormFormat, QueryStringBindable:
//  case ContainerKills   extends LogMetricId(asString = "container-kills"   , displayString = "Container Kills"   )
//  case NonIndexedQuery  extends LogMetricId(asString = "non-indexed-query" , displayString = "Non-indexed Queries" )
//  case SlowRunningQuery extends LogMetricId(asString = "slow-running-query", displayString = "Slow Running Queries")
