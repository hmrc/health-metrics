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

package uk.gov.hmrc.healthmetrics.controllers

import play.api.Logging
import play.api.libs.json.{Json, Writes, __}
import play.api.mvc.{Action, AnyContent, ControllerComponents, RequestHeader}
import uk.gov.hmrc.healthmetrics.connector.{LeakDetectionConnector, PlatformInitiativesConnector, ReleasesConnector, ServiceCommissioningStatusConnector, ServiceDependenciesConnector, ServiceMetricsConnector, ShutterConnector, TeamsAndRepositoriesConnector, VulnerabilitiesConnector}
import uk.gov.hmrc.healthmetrics.model.{HealthMetric, LatestHealthMetrics, TeamName}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class HealthMetricsController @Inject()(
  cc                                 : ControllerComponents
, teamsAndRepositoriesConnector      : TeamsAndRepositoriesConnector
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
) extends BackendController(cc) 
    with  Logging:

  private val now: java.time.LocalDate = java.time.LocalDate.now()
  
  def latestTeamHealthMetrics(team: String): Action[AnyContent] =
    Action.async: request =>
      given RequestHeader               = request
      given Writes[LatestHealthMetrics] = LatestHealthMetrics.writes
      for
        openPRsForRepos               <- teamsAndRepositoriesConnector.openPullRequestsForReposOwnedByTeam(TeamName(team))
        openPRsByMembersOfTeam        <- teamsAndRepositoriesConnector.openPullRequestsRaisedByMembersOfTeam(TeamName(team))
        leaks                         <- leakDetectionConnector.leakDetectionRepoSummaries(
                                           team             = Some(TeamName(team))
                                         , digitalService   = None
                                         , includeNonIssues = false
                                         , includeBranches  = false
                                         ).map(_.count(_.unresolvedCount > 0))
        bobbyErrors                   =  (flag: String) =>
                                           serviceDependenciesConnector.bobbyReports(team = Some(TeamName(team)), flag = flag)
                                             .map(_.flatMap(_.violations.filter(v => !v.exempt && now.isAfter(v.from))).size)
        productionBobbyErrors         <- bobbyErrors("production")
        latestBobbyErrors             <- bobbyErrors("latest")
        bobbyWarnings                 =  (flag: String) =>
                                          serviceDependenciesConnector.bobbyReports(team = Some(TeamName(team)), flag = flag)
                                            .map(_.flatMap(_.violations.filter(v => !v.exempt && (now.isBefore(v.from) || now.isEqual(v.from)))).size)
        productionBobbyWarnings       <- bobbyWarnings("production")
        latestBobbyWarnings           <- bobbyWarnings("latest")
        actionRequiredVulnerabilities =  (flag: String) =>
                                           vulnerabilitiesConnector.vulnerabilityCounts(flag = flag, team = Some(TeamName(team)))
                                             .map(_.map(_.actionRequired).sum)
        productionVulnerabilities     <- actionRequiredVulnerabilities("production")
        latestVulnerabilities         <- actionRequiredVulnerabilities("latest")
        platformInitiatives           <- platformInitiativesConnector.getInitiatives(teamName = Some(TeamName(team)))
                                           .map(_.count(_.progress.percent < 100))
        commissioningStateWarnings    <- serviceCommissioningStatusConnector.cachedCommissioningStatus(teamName = Some(TeamName(team)))
                                           .map(_.count(_.warnings.exists(_.nonEmpty)))
        shutterStates                 =  (shutterType: String) =>
                                           shutterConnector.getShutterStates(shutterType, "production", teamName = Some(TeamName(team)))
                                             .map(_.count(x => x.shutterType == shutterType && x.status == "shuttered"))
        frontendShutterStates         <- shutterStates("frontend")
        apiShutterStates              <- shutterStates("api")
        serviceMetrics                <- serviceMetricsConnector.metrics(Some("production"), Some(TeamName(team)))
        serviceMetricsCounts          =  (id: String) => serviceMetrics.filter(_.id == id).map(_.logCount).sum
        prodDeploymentCounts          <- releasesConnector.releases(teamName = Some(TeamName(team))).map:
                                           _.map: wrw =>
                                             ( wrw.versions.maxBy(_.version)
                                             , wrw.versions.find(_.environment == "production")
                                             ) match
                                               case (v1, Some(v2)) if v2.version.patch >  0
                                                                   || v2.version.major <  v1.version.major
                                                                   || v2.version.minor <= v1.version.minor - 1
                                                                   => 1
                                               case _              => 0
                                         .map(counts => counts.sum)
        testJobs                      <- teamsAndRepositoriesConnector.findTestJobs(teamName = Some(TeamName(team)))
        testFailures                  =  testJobs.flatMap(_.latestBuild).count(_.result.contains("FAILURE"))
        accessibilityViolations       =  testJobs.flatMap(_.latestBuild).flatMap(_.testJobResults).flatMap(_.numAccessibilityViolations).sum
        securityAlerts                =  testJobs.flatMap(_.latestBuild).flatMap(_.testJobResults).flatMap(_.numSecurityAlerts).sum
        metrics                       =  LatestHealthMetrics(
                                           Map(
                                             HealthMetric.OpenPRForReposOwnedByTeam               -> openPRsForRepos
                                           , HealthMetric.OpenPRRaisedByMembersOfTeam             -> openPRsByMembersOfTeam
                                           , HealthMetric.LeakDetectionSummaries                  -> leaks
                                           , HealthMetric.ProductionBobbyErrors                   -> productionBobbyErrors
                                           , HealthMetric.LatestBobbyErrors                       -> latestBobbyErrors
                                           , HealthMetric.ProductionBobbyWarnings                 -> productionBobbyWarnings
                                           , HealthMetric.LatestBobbyWarnings                     -> latestBobbyWarnings
                                           , HealthMetric.ProductionActionRequiredVulnerabilities -> productionVulnerabilities
                                           , HealthMetric.LatestActionRequiredVulnerabilities     -> latestVulnerabilities
                                           , HealthMetric.PlatformInitiatives                     -> platformInitiatives
                                           , HealthMetric.ServiceCommissioningStateWarnings       -> commissioningStateWarnings
                                           , HealthMetric.FrontendShutterStates                   -> frontendShutterStates
                                           , HealthMetric.ApiShutterStates                        -> apiShutterStates
                                           , HealthMetric.ContainerKills                          -> serviceMetricsCounts("container-kills")
                                           , HealthMetric.NonIndexedQueries                       -> serviceMetricsCounts("non-indexed-queries")
                                           , HealthMetric.SlowRunningQueries                      -> serviceMetricsCounts("slow-running-queries")
                                           , HealthMetric.OutdatedOrHotFixedProductionDeployments -> prodDeploymentCounts
                                           , HealthMetric.TestFailures                            -> testFailures
                                           , HealthMetric.AccessibilityAssessmentViolations       -> accessibilityViolations
                                           , HealthMetric.SecurityAssessmentAlerts                -> securityAlerts
                                           )
                                         )
      yield Ok(Json.toJson(metrics))
