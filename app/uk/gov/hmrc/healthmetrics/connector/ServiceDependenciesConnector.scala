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

import play.api.Logger
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.healthmetrics.connector.ServiceDependenciesConnector.BobbyReport
import uk.gov.hmrc.healthmetrics.model.{DigitalService, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceDependenciesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using
  ec: ExecutionContext
):

  import uk.gov.hmrc.http.HttpReads.Implicits._
  
  private val url: String = 
    servicesConfig.baseUrl("service-dependencies")

  def bobbyReports(
    team          : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  , flag          : String
  )(using HeaderCarrier): Future[Seq[BobbyReport]] =
    given Reads[BobbyReport] = BobbyReport.reads
    httpClientV2
      .get(url"$url/api/bobbyReports?team=${team.map(_.asString)}&digitalService=${digitalService.map(_.asString)}&flag=$flag")
      .execute[Seq[BobbyReport]]
      //.map(_.filter(v => !v.exempt && now.isAfter(v.from))).size


object ServiceDependenciesConnector:
  case class Violation(
    from  : LocalDate
  , exempt: Boolean
  )
  
  private object Violation:
    val reads: Reads[Violation] =
      ( (__ \ "from"  ).read[LocalDate]
      ~ (__ \ "exempt").read[Boolean]
      )(Violation.apply _)
  
  
  case class BobbyReport(violations: Seq[Violation])
  
  object BobbyReport:
    val reads: Reads[BobbyReport] =
      given Reads[Violation] = Violation.reads
      (__ \ "violations").read[Seq[Violation]].map(BobbyReport.apply)
  