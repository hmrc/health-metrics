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

import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VulnerabilitiesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using
  ExecutionContext
):
  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("vulnerabilities")

  def vulnerabilityCounts(
    flag          : String
  , team          : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  )(using
    HeaderCarrier
  ): Future[Seq[TotalVulnerabilityCount]] =
    given Reads[TotalVulnerabilityCount] = TotalVulnerabilityCount.reads
    httpClientV2
      .get(url"$url/vulnerabilities/api/reports/${flag}/counts?team=${team.map(_.asString)}&digitalService=${digitalService.map(_.asString)}")
      .execute[Seq[TotalVulnerabilityCount]]


case class TotalVulnerabilityCount(
  actionRequired: Int
)

object TotalVulnerabilityCount:
  val reads: Reads[TotalVulnerabilityCount] =
    (__ \ "actionRequired").read[Int].map(TotalVulnerabilityCount.apply)