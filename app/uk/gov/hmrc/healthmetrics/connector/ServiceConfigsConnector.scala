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

import play.api.libs.functional.syntax.*
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.healthmetrics.connector.ServiceConfigsConnector.{AppRoutes, FrontendRoute}
import uk.gov.hmrc.healthmetrics.model.Environment
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceConfigsConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using
  ec: ExecutionContext
):

  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String = 
    servicesConfig.baseUrl("service-configs")

  def appRoutes(
    serviceName: String
  , version    : String
  )(using HeaderCarrier): Future[Option[AppRoutes]] =
    given Reads[AppRoutes] = AppRoutes.reads
    httpClientV2
      .get(url"$url/service-configs/app-routes/$serviceName/$version")
      .execute[Option[AppRoutes]]

  def frontendRoutes(
    serviceName: String,
    environment: Environment
  )(using HeaderCarrier): Future[Seq[FrontendRoute]] =
    given Reads[FrontendRoute] = FrontendRoute.reads
    httpClientV2
      .get(url"$url/service-configs/routes?serviceName=$serviceName&environment=${environment.asString}")
      .execute[Seq[FrontendRoute]]

object ServiceConfigsConnector:
  case class AppRoutes(
    service: String,
    version: String,
    paths  : Seq[String]
  )

  object AppRoutes:
    val reads: Reads[AppRoutes] =
      ( (__ \ "service").read[String]
      ~ (__ \ "version").read[String]
      ~ (__ \ "routes" ).read[Seq[String]](Reads.seq((__ \ "path").read[String]))
      )(apply)

  case class FrontendRoute(
    path   : String,
    isRegex: Boolean
  )

  object FrontendRoute:
    val reads: Reads[FrontendRoute] =
      ( (__ \ "path"   ).read[String]
      ~ (__ \ "isRegex").read[Boolean]
      )(apply)
