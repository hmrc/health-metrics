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
import play.api.libs.json.{JsValue, Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.healthmetrics.connector.TeamsAndRepositoriesConnector.JenkinsJob
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, TeamName, RepoName}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class TeamsAndRepositoriesConnector @Inject()(
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(using
  ExecutionContext
):

  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("teams-and-repositories")

  def allRepos()(using HeaderCarrier): Future[Seq[TeamsAndRepositoriesConnector.Repo]] =
    given Reads[TeamsAndRepositoriesConnector.Repo] = TeamsAndRepositoriesConnector.Repo.reads
    httpClientV2
      .get(url"$url/api/v2/repositories?organisation=mdtp&archived=false")
      .execute[Seq[TeamsAndRepositoriesConnector.Repo]]

  def allTeams()(using HeaderCarrier): Future[Seq[TeamsAndRepositoriesConnector.GitHubTeam]] =
    given Reads[TeamsAndRepositoriesConnector.GitHubTeam] = TeamsAndRepositoriesConnector.GitHubTeam.reads
    httpClientV2
     .get(url"$url/api/v2/teams")
     .execute[Seq[TeamsAndRepositoriesConnector.GitHubTeam]]

  def allDigitalServices()(using HeaderCarrier): Future[Seq[DigitalService]] =
    given Reads[DigitalService] = DigitalService.reads
    httpClientV2
      .get(url"$url/api/v2/digital-services")
      .execute[Seq[DigitalService]]

  def openPullRequestsForReposOwnedByTeam(team: TeamName)(using HeaderCarrier): Future[Int] =
    httpClientV2
      .get(url"$url/api/open-pull-requests?reposOwnedByTeam=${team.asString}")
      .execute[Seq[JsValue]]
      .map(_.size)

  def openPullRequestsForReposOwnedByDigitalService(digitalService: DigitalService)(using HeaderCarrier): Future[Int] =
    httpClientV2
      .get(url"$url/api/open-pull-requests?reposOwnedByDigitalService=${digitalService.asString}")
      .execute[Seq[JsValue]]
      .map(_.size)

  def openPullRequestsRaisedByMembersOfTeam(team: TeamName)(using HeaderCarrier): Future[Int] =
    httpClientV2
      .get(url"$url/api/open-pull-requests?raisedByMembersOfTeam=${team.asString}")
      .execute[Seq[JsValue]]
      .map(_.size)

  def findTestJobs(metricFilter: MetricFilter)(using HeaderCarrier): Future[Seq[JenkinsJob]] =
    given Reads[JenkinsJob] = JenkinsJob.reads

    val params: MetricFilter => Map[String, String] =
      case TeamName(name)       => Map("teamName"       -> name)
      case DigitalService(name) => Map("digitalService" -> name)

    httpClientV2
      .get(url"$url/api/test-jobs?${params(metricFilter)}")
      .execute[Seq[JenkinsJob]]


object TeamsAndRepositoriesConnector:

  case class Repo(repoName: RepoName, teamNames: Seq[TeamName],  endOfLifeDate: Option[Instant], isDeprecated: Boolean = false)
  object Repo:
    val reads: Reads[Repo] =
      ( (__ \ "name"         ).read[String].map(RepoName.apply)
      ~ (__ \ "teamNames"    ).read[Seq[TeamName]]
      ~ (__ \ "endOfLifeDate").readNullable[Instant]
      ~ (__ \ "isDeprecated" ).readWithDefault[Boolean](false)
      )(Repo.apply _)

  case class GitHubTeam(
    name           : TeamName,
    lastActiveDate : Option[Instant],
    repos          : Seq[String]
  )

  object GitHubTeam:
    val reads: Reads[GitHubTeam] =
      ( (__ \ "name"          ).read[TeamName]
      ~ (__ \ "lastActiveDate").readNullable[Instant]
      ~ (__ \ "repos"         ).read[Seq[String]]
      )(GitHubTeam.apply)

  case class TestJobResults(
    numAccessibilityViolations : Option[Int]
  , numSecurityAlerts          : Option[Int]
  )

  object TestJobResults:
    val reads: Reads[TestJobResults] =
      ( (__ \ "numAccessibilityViolations").readNullable[Int]
      ~ (__ \ "numSecurityAlerts"         ).readNullable[Int]
      )(TestJobResults.apply)

  case class BuildData(
    result        : Option[String],
    testJobResults: Option[TestJobResults] = None
  )

  object BuildData:
    val reads: Reads[BuildData] =
      ( (__ \ "result"        ).readNullable[String]
      ~ (__ \ "testJobResults").readNullable[TestJobResults](TestJobResults.reads)
      )(BuildData.apply)

  case class JenkinsJob(latestBuild: Option[BuildData])

  object JenkinsJob:
    val reads: Reads[JenkinsJob] =
      (__ \ "latestBuild").readNullable[BuildData](BuildData.reads).map(JenkinsJob.apply)
