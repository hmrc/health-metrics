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


import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.healthmetrics.model.{Environment, ServiceName, Version}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ServiceConfigsConnector @Inject()(
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
):
  import HttpReads.Implicits.*

  private given HeaderCarrier = HeaderCarrier()

  private val serviceUrl =
    servicesConfig.baseUrl("service-configs")

  def getBobbyRules(): Future[ServiceConfigsConnector.BobbyRules] =
    given Reads[ServiceConfigsConnector.BobbyRules] = ServiceConfigsConnector.BobbyRules.reads
    httpClientV2
      .get(url"$serviceUrl/service-configs/bobby/rules")
      .execute[ServiceConfigsConnector.BobbyRules]

  def getServiceRelationships(serviceName: ServiceName): Future[Option[ServiceConfigsConnector.ServiceRelationships]] =
    given Reads[ServiceConfigsConnector.ServiceRelationships] = ServiceConfigsConnector.ServiceRelationships.reads
    httpClientV2
      .get(url"$serviceUrl/service-configs/service-relationships/${serviceName.asString}")
      .execute[Option[ServiceConfigsConnector.ServiceRelationships]]

  def appRoutes(
    serviceName: ServiceName
  , version    : Version
  )(using HeaderCarrier): Future[Option[ServiceConfigsConnector.AppRoutes]] =
    given Reads[ServiceConfigsConnector.AppRoutes] = ServiceConfigsConnector.AppRoutes.reads
    httpClientV2
      .get(url"$serviceUrl/service-configs/app-routes/${serviceName.asString}/$version")
      .execute[Option[ServiceConfigsConnector.AppRoutes]]

  def frontendRoutes(
    serviceName: ServiceName
  , environment: Environment
  )(using HeaderCarrier): Future[Seq[ServiceConfigsConnector.FrontendRoute]] =
    given Reads[ServiceConfigsConnector.FrontendRoute] = ServiceConfigsConnector.FrontendRoute.reads
    httpClientV2
      .get(url"$serviceUrl/service-configs/routes?serviceName=${serviceName.asString}&environment=${environment.asString}")
      .execute[Seq[ServiceConfigsConnector.FrontendRoute]]

object ServiceConfigsConnector:
  import play.api.libs.functional.syntax.*

  case class BobbyRules(
    libraries: Seq[BobbyRules.BobbyRule]
  , plugins  : Seq[BobbyRules.BobbyRule]
  )

  object BobbyRules:
    case class BobbyRule(
      group         : String
    , artefact      : String
    , versionRange  : String
    , reason        : String
    , from          : LocalDate
    , exemptProjects: Seq[String]
    )

    private val readsBobbyRule: Reads[BobbyRule] =
      ( (__ \ "organisation"  ).read[String]
      ~ (__ \ "name"          ).read[String]
      ~ (__ \ "range"         ).read[String]
      ~ (__ \ "reason"        ).read[String]
      ~ (__ \ "from"          ).read[LocalDate]
      ~ (__ \ "exemptProjects").read[Seq[String]]
      )(BobbyRule.apply _)

    val reads: Reads[BobbyRules] =
      ( (__ \ "libraries").lazyRead(Reads.seq[BobbyRule](readsBobbyRule))
      ~ (__ \ "plugins"  ).lazyRead(Reads.seq[BobbyRule](readsBobbyRule))
      )(BobbyRules.apply _)

  case class ServiceRelationships(
    inboundServices : Set[ServiceName],
    outboundServices: Set[ServiceName]
  )

  object ServiceRelationships:
    val reads: Reads[ServiceRelationships] =
      ( (__ \ "inboundServices" ).read[Set[String]].map(_.map(ServiceName.apply _))
      ~ (__ \ "outboundServices").read[Set[String]].map(_.map(ServiceName.apply _))
      )(ServiceRelationships.apply _)

  case class AppRoutes(
    service: ServiceName
  , version: Version
  , paths  : Seq[String]
  )

  object AppRoutes:
    given Reads[Version] = Version.reads
    val reads: Reads[AppRoutes] =
      ( (__ \ "service").read[String].map(ServiceName.apply _)
      ~ (__ \ "version").read[Version]
      ~ (__ \ "routes" ).read[Seq[String]](Reads.seq((__ \ "path").read[String]))
      )(AppRoutes.apply _)

  case class FrontendRoute(
    path   : String
  , isRegex: Boolean
  )

  object FrontendRoute:
    val reads: Reads[FrontendRoute] =
      ( (__ \ "path"   ).read[String]
      ~ (__ \ "isRegex").read[Boolean]
      )(FrontendRoute.apply _)
