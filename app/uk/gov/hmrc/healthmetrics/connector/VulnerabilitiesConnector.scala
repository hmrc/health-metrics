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
import play.api.libs.functional.syntax.*
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, SlugInfoFlag, TeamName}

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
    metricFilter: MetricFilter
  , flag        : SlugInfoFlag
  )(using
    HeaderCarrier
  ): Future[Seq[VulnerabilitiesConnector.TotalVulnerabilityCount]] =
    given Reads[VulnerabilitiesConnector.TotalVulnerabilityCount] = VulnerabilitiesConnector.TotalVulnerabilityCount.reads

    val params: MetricFilter => Map[String, String] =
      case TeamName(name)       => Map("team"           -> name)
      case DigitalService(name) => Map("digitalService" -> name)

    httpClientV2
      .get(url"$url/vulnerabilities/api/reports/${flag.asString}/counts?${params(metricFilter)}")
      .execute[Seq[VulnerabilitiesConnector.TotalVulnerabilityCount]]

  def vulnerabilitySummaries(
    serviceName: Option[String] = None
  , version    : Option[String] = None
  , flag       : Option[String] = None
  )(using
    HeaderCarrier
  ): Future[Seq[VulnerabilitiesConnector.DistinctVulnerability]] =
    given Reads[VulnerabilitiesConnector.DistinctVulnerability] = VulnerabilitiesConnector.DistinctVulnerability.reads

    httpClientV2
      .get(url"$url/vulnerabilities/api/summaries?service=${serviceName.map(sn => s"\"$sn\"")}&version=$version&flag=$flag&curationStatus=ACTION_REQUIRED")
      .execute[Seq[VulnerabilitiesConnector.DistinctVulnerability]]


object VulnerabilitiesConnector:
  case class TotalVulnerabilityCount(
    actionRequired: Int
  )

  object TotalVulnerabilityCount:
    val reads: Reads[TotalVulnerabilityCount] =
      (__ \ "actionRequired").read[Int].map(TotalVulnerabilityCount.apply)

  case class DistinctVulnerability(
    vulnerableComponentName   : String,
    vulnerableComponentVersion: String,
    id                        : String,
    occurrences               : Seq[VulnerabilityOccurrence]
  ):
    def matchesGav(group: String, artefact: String, version: String, scalaVersion: Option[String]): Boolean =
      occurrences.exists(_.matchesGav(group, artefact, version, scalaVersion))

  object DistinctVulnerability:
    val reads: Reads[DistinctVulnerability] =
      given Reads[VulnerabilityOccurrence] = VulnerabilityOccurrence.reads
      ( (__ \ "distinctVulnerability" \ "vulnerableComponentName"   ).read[String]
      ~ (__ \ "distinctVulnerability" \ "vulnerableComponentVersion").read[String]
      ~ (__ \ "distinctVulnerability" \ "id"                        ).read[String]
      ~ (__ \ "occurrences"                                         ).read[Seq[VulnerabilityOccurrence]]
      )(apply)

  case class VulnerabilityOccurrence(
    vulnerableComponentName   : String,
    vulnerableComponentVersion: String,
    componentPathInSlug       : String,
    teams                     : Seq[TeamName],
    service                   : String
  ):

    def matchesGav(group: String, artefact: String, version: String, scalaVersion: Option[String]): Boolean =
      if this.vulnerableComponentName == s"gav://$group:$artefact${scalaVersion.fold("")("_" + _)}" && this.vulnerableComponentVersion == version
      then
        true
      else
        componentPathInSlug match
          case VulnerabilityOccurrence.jarRegex(jar) =>
            jar == s"$group.$artefact${scalaVersion.fold("")("_" + _)}-$version"
              // java wars have jars without the group in the name
              || jar == s"$artefact${scalaVersion.fold("")("_" + _)}-$version"
          case _ => false

  object VulnerabilityOccurrence:
    private val jarRegex = raw".*\/([^\/]+)\.jar.*".r

    val reads: Reads[VulnerabilityOccurrence] =
      ( (__ \ "vulnerableComponentName"   ).read[String]
      ~ (__ \ "vulnerableComponentVersion").read[String]
      ~ (__ \ "componentPathInSlug"       ).read[String]
      ~ (__ \ "teams"                     ).read[Seq[TeamName]]
      ~ (__ \ "service"                   ).read[String]
      )(apply)
