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
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.healthmetrics.connector.PlatformInitiativesConnector.PlatformInitiative
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatformInitiativesConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext):
  import HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("platform-initiatives")

  private given Reads[PlatformInitiative] = PlatformInitiative.reads

  def getInitiatives(metricFilter: MetricFilter)(using HeaderCarrier): Future[Seq[PlatformInitiative]] =
    val params: MetricFilter => Map[String, String] =
      case TeamName(name)       => Map("teamName"       -> name)
      case DigitalService(name) => Map("digitalService" -> name)

    httpClientV2
      .get(url"$url/platform-initiatives/initiatives?${params(metricFilter)}")
      .execute[Seq[PlatformInitiative]]

object PlatformInitiativesConnector:
  case class Progress(
    current : Int,
    target  : Int,
  ):
    def percent: Int =
      if target == 0
      then 0
      else (current.toFloat / target.toFloat * 100).toInt

  object Progress:
    val reads: Reads[Progress] =
      ( (__ \ "current").read[Int]
      ~ (__ \ "target" ).read[Int]
      )(Progress.apply)

  case class PlatformInitiative(progress: Progress)

  object PlatformInitiative:
    val reads: Reads[PlatformInitiative] =
      (__ \ "progress").read[Progress](Progress.reads).map(PlatformInitiative.apply)
