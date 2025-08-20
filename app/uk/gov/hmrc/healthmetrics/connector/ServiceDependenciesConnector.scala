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
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.healthmetrics.connector.ServiceDependenciesConnector.BobbyReport
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, SlugInfoFlag, TeamName, ServiceName, RepoName}

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
    metricFilter: Option[MetricFilter] = None
  , flag        : SlugInfoFlag
  )(using HeaderCarrier): Future[Seq[BobbyReport]] =

    val params: Option[MetricFilter] => Map[String, String] =
      case Some(TeamName(name)      ) => Map("team"           -> name)
      case Some(DigitalService(name)) => Map("digitalService" -> name)
      case None                       => Map.empty

    given Reads[BobbyReport] = BobbyReport.reads
    httpClientV2
      .get(url"$url/api/bobbyReports?${params(metricFilter)}&flag=${flag.asString}")
      .execute[Seq[BobbyReport]]

  def getAffectedServices(
    group: String
  , artefact: String
  , versionRange: String
  )(using HeaderCarrier): Future[Seq[ServiceDependenciesConnector.AffectedService]] =
    given Reads[ServiceDependenciesConnector.AffectedService] = ServiceDependenciesConnector.AffectedService.reads
    httpClientV2
      .get(url"$url/api/repoDependencies?group=$group&artefact=$artefact&versionRange=$versionRange&repoType=Service")
      .execute[Seq[ServiceDependenciesConnector.AffectedService]]

object ServiceDependenciesConnector:
  case class BobbyReport(repoName: RepoName, violations: Seq[BobbyReport.Violation])
  object BobbyReport:
    case class Violation(
      from  : LocalDate
    , exempt: Boolean
    )

    val reads: Reads[BobbyReport] =
      given Reads[Violation] =
        ( (__ \ "from"  ).read[LocalDate]
        ~ (__ \ "exempt").read[Boolean]
        )(Violation.apply _)

      ( (__ \ "repoName"  ).read[String].map(RepoName.apply _)
      ~ (__ \ "violations").read[Seq[Violation]]
      )(BobbyReport.apply)

  case class AffectedService(serviceName: ServiceName, teamNames: List[TeamName])

  object AffectedService:
    val reads: Reads[AffectedService] =
      ( (__ \ "repoName").read[String      ].map(ServiceName.apply)
      ~ (__ \ "teams"   ).read[List[String]].map(_.map(TeamName.apply))
      )(AffectedService.apply _)
