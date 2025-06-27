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

package uk.gov.hmrc.healthmetrics.service

import cats.implicits.*
import uk.gov.hmrc.healthmetrics.connector.{LeakDetectionConnector, PlatformInitiativesConnector, ReleasesConnector, ServiceCommissioningStatusConnector, ServiceDependenciesConnector, ServiceMetricsConnector, ShutterConnector, TeamsAndRepositoriesConnector, VulnerabilitiesConnector}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, Environment, HealthMetric, LogMetricId, MetricFilter, ShutterStatusValue, ShutterType, SlugInfoFlag, TeamName}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HealthMetricsService @Inject()(
  teamsAndRepositoriesConnector      : TeamsAndRepositoriesConnector
, leakDetectionConnector             : LeakDetectionConnector
, serviceDependenciesConnector       : ServiceDependenciesConnector
, vulnerabilitiesConnector           : VulnerabilitiesConnector
, platformInitiativesConnector       : PlatformInitiativesConnector
, serviceCommissioningStatusConnector: ServiceCommissioningStatusConnector
, shutterConnector                   : ShutterConnector
, serviceMetricsConnector            : ServiceMetricsConnector
, releasesConnector                  : ReleasesConnector
)(using
  ExecutionContext
):

  private[service] def sequence(m: Map[HealthMetric, Future[Int]]): Future[Map[HealthMetric, Int]] =
    m.toSeq.map((k, v) => v.map(k -> _)).sequence.map(_.toMap)

  def generateHealthMetrics(
    metricFilter: MetricFilter
  , currentDate : LocalDate
  )(using HeaderCarrier): Future[Map[HealthMetric, Int]] =
    ( openPRMetrics(metricFilter)
    , teamsAndRepositoriesConnector.findTestJobs(metricFilter)
    , serviceMetricsConnector.metrics(metricFilter, Environment.Production)
    ).flatMapN: (openPRs, testJobs, serviceMetrics) =>
      val serviceMetricsCounts = (id: LogMetricId) => serviceMetrics.filter(_.id == id).map(_.logCount).sum
      sequence(Map(
        HealthMetric.LeakDetectionSummaries                  -> countLeaks(metricFilter)
      , HealthMetric.ProductionBobbyErrors                   -> bobbyErrors(metricFilter, SlugInfoFlag.Production, currentDate)
      , HealthMetric.LatestBobbyErrors                       -> bobbyErrors(metricFilter, SlugInfoFlag.Latest    , currentDate)
      , HealthMetric.ProductionBobbyWarnings                 -> bobbyWarnings(metricFilter, SlugInfoFlag.Production, currentDate)
      , HealthMetric.LatestBobbyWarnings                     -> bobbyWarnings(metricFilter, SlugInfoFlag.Latest    , currentDate)
      , HealthMetric.ProductionActionRequiredVulnerabilities -> actionRequiredVulnerabilities(metricFilter, SlugInfoFlag.Production)
      , HealthMetric.LatestActionRequiredVulnerabilities     -> actionRequiredVulnerabilities(metricFilter, SlugInfoFlag.Latest    )
      , HealthMetric.PlatformInitiatives                     -> countIncompleteInitiatives(metricFilter)
      , HealthMetric.ServiceCommissioningStateWarnings       -> countCommissioningWarnings(metricFilter)
      , HealthMetric.FrontendShutterStates                   -> countShutterStates(metricFilter, ShutterType.Frontend)
      , HealthMetric.ApiShutterStates                        -> countShutterStates(metricFilter, ShutterType.Api     )
      , HealthMetric.OutdatedOrHotFixedProductionDeployments -> countOutdatedDeployments(metricFilter)
      , HealthMetric.ContainerKills                          -> Future.successful(serviceMetricsCounts(LogMetricId.ContainerKills))
      , HealthMetric.NonIndexedQueries                       -> Future.successful(serviceMetricsCounts(LogMetricId.NonIndexedQuery))
      , HealthMetric.SlowRunningQueries                      -> Future.successful(serviceMetricsCounts(LogMetricId.SlowRunningQuery))
      , HealthMetric.TestFailures                            -> Future.successful(testJobs.flatMap(_.latestBuild).count(_.result.contains("FAILURE")))
      , HealthMetric.AccessibilityAssessmentViolations       -> Future.successful(testJobs.flatMap(_.latestBuild).flatMap(_.testJobResults).flatMap(_.numAccessibilityViolations).sum)
      , HealthMetric.SecurityAssessmentAlerts                -> Future.successful(testJobs.flatMap(_.latestBuild).flatMap(_.testJobResults).flatMap(_.numSecurityAlerts).sum)
      ))
        .map(openPRs ++ _)

  private def openPRMetrics(metricFilter: MetricFilter)(using HeaderCarrier): Future[Map[HealthMetric, Int]] =
    sequence(
      metricFilter match
        case digi: DigitalService => Map(HealthMetric.OpenPRForOwnedRepos       -> teamsAndRepositoriesConnector.openPullRequestsForReposOwnedByDigitalService(digi))
        case team: TeamName       => Map(
                                       HealthMetric.OpenPRForOwnedRepos         -> teamsAndRepositoriesConnector.openPullRequestsForReposOwnedByTeam(team)
                                     , HealthMetric.OpenPRRaisedByMembersOfTeam -> teamsAndRepositoriesConnector.openPullRequestsRaisedByMembersOfTeam(team)
                                     )
    )

  private def bobbyErrors(
    metricFilter: MetricFilter
  , flag        : SlugInfoFlag
  , now         : LocalDate
  )(using HeaderCarrier): Future[Int] =
    serviceDependenciesConnector
      .bobbyReports(metricFilter, flag)
      .map(_.flatMap(_.violations.filter(v => !v.exempt && now.isAfter(v.from))).size)

  private def bobbyWarnings(
    metricFilter: MetricFilter
  , flag        : SlugInfoFlag
  , now         : LocalDate
  )(using HeaderCarrier): Future[Int] =
    serviceDependenciesConnector
      .bobbyReports(metricFilter, flag)
      .map(_.flatMap(_.violations.filter(v => !v.exempt && (now.isBefore(v.from) || now.isEqual(v.from)))).size)

  private def actionRequiredVulnerabilities(
    metricFilter: MetricFilter
  , flag        : SlugInfoFlag
  )(using HeaderCarrier): Future[Int] =
    vulnerabilitiesConnector
      .vulnerabilityCounts(metricFilter, flag)
      .map(_.map(_.actionRequired).sum)

  private def countLeaks(
    metricFilter: MetricFilter
  )(using HeaderCarrier): Future[Int] =
    leakDetectionConnector
      .leakDetectionRepoSummaries(metricFilter)
      .map(_.count(_.unresolvedCount > 0))

  private def countIncompleteInitiatives(
    metricFilter: MetricFilter
  )(using HeaderCarrier): Future[Int] =
    platformInitiativesConnector
      .getInitiatives(metricFilter)
      .map(_.count(_.progress.percent < 100))

  private def countCommissioningWarnings(
    metricFilter: MetricFilter
  )(using HeaderCarrier): Future[Int] =
    serviceCommissioningStatusConnector
      .cachedCommissioningStatus(metricFilter)
      .map(_.count(_.warnings.exists(_.nonEmpty)))

  private def countShutterStates(
    metricFilter: MetricFilter
  , shutterType : ShutterType
  )(using HeaderCarrier): Future[Int] =
    shutterConnector
      .getShutterStates(st = shutterType, env = Environment.Production, metricFilter = metricFilter)
      .map(_.count(x => x.shutterType == shutterType && x.status == ShutterStatusValue.Shuttered))

  private def countOutdatedDeployments(
    metricFilter: MetricFilter
  )(using HeaderCarrier): Future[Int] =
    releasesConnector
      .releases(metricFilter)
      .map(_.map: wrw =>
        (wrw.versions.maxBy(_.version), wrw.versions.find(_.environment == Environment.Production.asString)) match
          case (v1, Some(v2)) if v2.version.patch > 0
                              || v2.version.major < v1.version.major
                              || v2.version.minor < v1.version.minor
                              => 1
          case _              => 0
      ).map(_.sum)
