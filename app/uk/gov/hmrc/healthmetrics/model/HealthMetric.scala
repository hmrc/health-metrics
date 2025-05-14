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

package uk.gov.hmrc.healthmetrics.model

import play.api.libs.json.{Reads, Writes, __}
import uk.gov.hmrc.healthmetrics.util.{FromString, FromStringEnum, Parser}
import FromStringEnum.*

//case class TeamHealthMetrics(team: TeamName, metrics: Map[HealthMetric, Int])
//case class DigitalServiceHealthMetrics(digitalService: String, metrics: Map[HealthMetric, Int])


given Parser[HealthMetric] = Parser.parser(HealthMetric.values)

enum HealthMetric(
  override val asString: String
) extends FromString
  derives Reads, Writes:
  case OpenPRRaisedByMembersOfTeam             extends HealthMetric(asString = "OPEN_PR_RAISED_BY_MEMBERS_OF_TEAM") //DONE
  case OpenPRForReposOwnedByTeam               extends HealthMetric(asString = "OPEN_PR_FOR_REPOS_OWNED_BY_TEAM") //DONE
  case OpenPRForReposOwnedByDigitalService     extends HealthMetric(asString = "OPEN_PR_FOR_REPOS_OWNED_BY_DIGITAL_SERVICE")
  case LeakDetectionSummaries                  extends HealthMetric(asString = "LEAK_DETECTION_SUMMARIES") //DONE
  case ProductionBobbyErrors                   extends HealthMetric(asString = "PRODUCTION_BOBBY_ERRORS") //DONE
  case LatestBobbyErrors                       extends HealthMetric(asString = "LATEST_BOBBY_ERRORS") //DONE
  case ProductionBobbyWarnings                 extends HealthMetric(asString = "PRODUCTION_BOBBY_WARNINGS") //DONE
  case LatestBobbyWarnings                     extends HealthMetric(asString = "LATEST_BOBBY_WARNINGS") //DONE
  case FrontendShutterStates                   extends HealthMetric(asString = "FRONTEND_SHUTTER_STATES") //DONE
  case ApiShutterStates                        extends HealthMetric(asString = "API_SHUTTER_STATES") //DONE
  case PlatformInitiatives                     extends HealthMetric(asString = "PLATFORM_INITIATIVES") //DONE
  case ProductionActionRequiredVulnerabilities extends HealthMetric(asString = "PRODUCTION_ACTION_REQUIRED_VULNERABILITIES") //DONE
  case LatestActionRequiredVulnerabilities     extends HealthMetric(asString = "LATEST_ACTION_REQUIRED_VULNERABILITIES") //DONE
  case ServiceCommissioningStateWarnings       extends HealthMetric(asString = "SERVICE_COMMISSIONING_STATE_WARNINGS")  //DONE
  case ContainerKills                          extends HealthMetric(asString = "CONTAINER_KILLS") //DONE
  case NonIndexedQueries                       extends HealthMetric(asString = "NON_INDEXED_QUERIES")// DONE
  case SlowRunningQueries                      extends HealthMetric(asString = "SLOW_RUNNING_QUERIES") //DONE
  case OutdatedOrHotFixedProductionDeployments extends HealthMetric(asString = "OUTDATED_OR_HOT_FIXED_PRODUCTION_DEPLOYMENTS") //DONE
  case TestFailures                            extends HealthMetric(asString = "TEST_FAILURES") //DONE
  case AccessibilityAssessmentViolations       extends HealthMetric(asString = "ACCESSIBILITY_ASSESSMENT_VIOLATIONS") //DONE
  case SecurityAssessmentAlerts                extends HealthMetric(asString = "SECURITY_ASSESSMENT_ALERTS") //DONE


case class LatestHealthMetrics(metrics: Map[HealthMetric, Int])

object LatestHealthMetrics:
  given Writes[HealthMetric] = HealthMetric.derived$Writes
  val writes: Writes[LatestHealthMetrics] =
    (__ \ "metrics").write[Map[HealthMetric, Int]]
      .contramap(_.metrics)