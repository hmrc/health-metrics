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
import uk.gov.hmrc.healthmetrics.connector.LeakDetectionConnector.LeakDetectionRepositorySummary
import uk.gov.hmrc.healthmetrics.model.{MetricFilter, TeamName, DigitalService}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LeakDetectionConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ec: ExecutionContext):
  
  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String = 
    servicesConfig.baseUrl("leak-detection")

  def leakDetectionRepoSummaries(
    metricFilter    : MetricFilter
  , includeNonIssues: Boolean = false
  , includeBranches : Boolean = false
  )(using
     HeaderCarrier
  ): Future[Seq[LeakDetectionRepositorySummary]] =
    given Reads[LeakDetectionRepositorySummary] = LeakDetectionRepositorySummary.reads
    val excludeNonIssues = !includeNonIssues

    val params: MetricFilter => Map[String, String] =
      case TeamName(name)       => Map("team"           -> name)
      case DigitalService(name) => Map("digitalService" -> name)

    httpClientV2
      .get(url"$url/api/repositories/summary?${params(metricFilter)}&excludeNonIssues=$excludeNonIssues&includeBranches=$includeBranches")
      .execute[Seq[LeakDetectionRepositorySummary]]
    
object LeakDetectionConnector:
  case class LeakDetectionRepositorySummary(
    warningCount   : Int
  , excludedCount  : Int
  , unresolvedCount: Int
  ):
    def totalCount: Int =
      warningCount + excludedCount + unresolvedCount
  
  object LeakDetectionRepositorySummary:
    val reads: Reads[LeakDetectionRepositorySummary] =
      ( (__ \ "warningCount"   ).read[Int]
      ~ (__ \ "excludedCount"  ).read[Int]
      ~ (__ \ "unresolvedCount").read[Int]
      )(LeakDetectionRepositorySummary.apply)
