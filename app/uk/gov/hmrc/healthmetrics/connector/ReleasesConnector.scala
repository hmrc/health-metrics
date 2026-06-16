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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsResult, Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.healthmetrics.connector.ReleasesConnector.WhatsRunningWhere
import uk.gov.hmrc.healthmetrics.model.{Environment, MetricFilter, TeamName, DigitalService, ServiceName, Version}

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReleasesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext):
  import HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("releases-api")

  private given Reads[WhatsRunningWhere] = WhatsRunningWhere.reads

  def releases(
    metricFilter: Option[MetricFilter] = None
  )(using HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    val params: Option[MetricFilter] => Map[String, String] =
      case Some(TeamName(name)      ) => Map("teamName"       -> name)
      case Some(DigitalService(name)) => Map("digitalService" -> name)
      case None                       => Map.empty

    httpClientV2
      .get(url"$url/releases-api/whats-running-where?${params(metricFilter)}")
      .execute[Seq[WhatsRunningWhere]]

object ReleasesConnector:
  case class WhatsRunningWhere(
    serviceName: ServiceName
  , deployments: List[WhatsRunningWhere.Deployment]
  )

  object WhatsRunningWhere:
    case class Deployment(
      environment : Environment
    , version     : Version
    , lastDeployed: Instant,
    )

    val reads: Reads[WhatsRunningWhere] =
      given Reads[Deployment] =
        ( (__ \ "environment"  ).read[Environment]
        ~ (__ \ "versionNumber").read[Version](Version.reads)
        ~ (__ \ "lastDeployed" ).read[Instant]
        )(Deployment.apply)

      ( (__ \ "applicationName").read[String].map(ServiceName.apply)
      ~ (__ \ "versions"       ).read[List[Deployment]]
      )(WhatsRunningWhere.apply)
