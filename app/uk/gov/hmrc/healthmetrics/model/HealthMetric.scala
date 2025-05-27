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

import play.api.libs.json.{Format, KeyWrites, Reads, Writes, __}
import uk.gov.hmrc.healthmetrics.util.{FromString, FromStringEnum, Parser}
import FromStringEnum.*
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.LocalDate


given Parser[HealthMetric] = Parser.parser(HealthMetric.values)

enum HealthMetric(
  override val asString: String
) extends FromString
  derives Reads, Writes:
  case OpenPRRaisedByMembersOfTeam             extends HealthMetric(asString = "OPEN_PR_RAISED_BY_MEMBERS_OF_TEAM"           )
  case OpenPRForReposOwnedByTeam               extends HealthMetric(asString = "OPEN_PR_FOR_REPOS_OWNED_BY_TEAM"             )
  case OpenPRForReposOwnedByDigitalService     extends HealthMetric(asString = "OPEN_PR_FOR_REPOS_OWNED_BY_DIGITAL_SERVICE"  )
  case LeakDetectionSummaries                  extends HealthMetric(asString = "LEAK_DETECTION_SUMMARIES"                    )
  case ProductionBobbyErrors                   extends HealthMetric(asString = "PRODUCTION_BOBBY_ERRORS"                     )
  case LatestBobbyErrors                       extends HealthMetric(asString = "LATEST_BOBBY_ERRORS"                         )
  case ProductionBobbyWarnings                 extends HealthMetric(asString = "PRODUCTION_BOBBY_WARNINGS"                   )
  case LatestBobbyWarnings                     extends HealthMetric(asString = "LATEST_BOBBY_WARNINGS"                       )
  case FrontendShutterStates                   extends HealthMetric(asString = "FRONTEND_SHUTTER_STATES"                     )
  case ApiShutterStates                        extends HealthMetric(asString = "API_SHUTTER_STATES"                          )
  case PlatformInitiatives                     extends HealthMetric(asString = "PLATFORM_INITIATIVES"                        )
  case ProductionActionRequiredVulnerabilities extends HealthMetric(asString = "PRODUCTION_ACTION_REQUIRED_VULNERABILITIES"  )
  case LatestActionRequiredVulnerabilities     extends HealthMetric(asString = "LATEST_ACTION_REQUIRED_VULNERABILITIES"      )
  case ServiceCommissioningStateWarnings       extends HealthMetric(asString = "SERVICE_COMMISSIONING_STATE_WARNINGS"        )
  case ContainerKills                          extends HealthMetric(asString = "CONTAINER_KILLS"                             )
  case NonIndexedQueries                       extends HealthMetric(asString = "NON_INDEXED_QUERIES"                         )
  case SlowRunningQueries                      extends HealthMetric(asString = "SLOW_RUNNING_QUERIES"                        )
  case OutdatedOrHotFixedProductionDeployments extends HealthMetric(asString = "OUTDATED_OR_HOT_FIXED_PRODUCTION_DEPLOYMENTS")
  case TestFailures                            extends HealthMetric(asString = "TEST_FAILURES"                               )
  case AccessibilityAssessmentViolations       extends HealthMetric(asString = "ACCESSIBILITY_ASSESSMENT_VIOLATIONS"         )
  case SecurityAssessmentAlerts                extends HealthMetric(asString = "SECURITY_ASSESSMENT_ALERTS"                  )


case class LatestHealthMetrics(metrics: Map[HealthMetric, Int])

object LatestHealthMetrics:
  val writes: Writes[LatestHealthMetrics] =
    given KeyWrites[HealthMetric] = KeyWrites.derived
    (__ \ "metrics").write[Map[HealthMetric, Int]].contramap(_.metrics)


case class TeamHealthMetricsHistory(
  teamName: TeamName
, date    : LocalDate
, metrics : Map[HealthMetric, Int]
)

object TeamHealthMetricsHistory:
  given Format[LocalDate]       = MongoJavatimeFormats.localDateFormat
  given KeyWrites[HealthMetric] = KeyWrites.derived

  val format: Format[TeamHealthMetricsHistory] =
    ( (__ \ "teamName").format[TeamName]
    ~ (__ \ "date"    ).format[LocalDate]
    ~ (__ \ "metrics" ).format[Map[HealthMetric, Int]]
    )(TeamHealthMetricsHistory.apply, pt => Tuple.fromProductTyped(pt))

  val apiWrites: Writes[TeamHealthMetricsHistory] =
    ( (__ \ "teamName").write[TeamName]
    ~ (__ \ "date"    ).write[String].contramap(_.toString)
    ~ (__ \ "metrics" ).write[Map[HealthMetric, Int]]
    )(pt => Tuple.fromProductTyped(pt))
