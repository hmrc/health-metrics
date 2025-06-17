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
  case OpenPRRaisedByMembersOfTeam             extends HealthMetric("OPEN_PR_RAISED_BY_MEMBERS_OF_TEAM"           )
  case OpenPRForReposOwnedByTeam               extends HealthMetric("OPEN_PR_FOR_REPOS_OWNED_BY_TEAM"             )
  case OpenPRForReposOwnedByDigitalService     extends HealthMetric("OPEN_PR_FOR_REPOS_OWNED_BY_DIGITAL_SERVICE"  )
  case LeakDetectionSummaries                  extends HealthMetric("LEAK_DETECTION_SUMMARIES"                    )
  case ProductionBobbyErrors                   extends HealthMetric("PRODUCTION_BOBBY_ERRORS"                     )
  case LatestBobbyErrors                       extends HealthMetric("LATEST_BOBBY_ERRORS"                         )
  case ProductionBobbyWarnings                 extends HealthMetric("PRODUCTION_BOBBY_WARNINGS"                   )
  case LatestBobbyWarnings                     extends HealthMetric("LATEST_BOBBY_WARNINGS"                       )
  case FrontendShutterStates                   extends HealthMetric("FRONTEND_SHUTTER_STATES"                     )
  case ApiShutterStates                        extends HealthMetric("API_SHUTTER_STATES"                          )
  case PlatformInitiatives                     extends HealthMetric("PLATFORM_INITIATIVES"                        )
  case ProductionActionRequiredVulnerabilities extends HealthMetric("PRODUCTION_ACTION_REQUIRED_VULNERABILITIES"  )
  case LatestActionRequiredVulnerabilities     extends HealthMetric("LATEST_ACTION_REQUIRED_VULNERABILITIES"      )
  case ServiceCommissioningStateWarnings       extends HealthMetric("SERVICE_COMMISSIONING_STATE_WARNINGS"        )
  case ContainerKills                          extends HealthMetric("CONTAINER_KILLS"                             )
  case NonIndexedQueries                       extends HealthMetric("NON_INDEXED_QUERIES"                         )
  case SlowRunningQueries                      extends HealthMetric("SLOW_RUNNING_QUERIES"                        )
  case OutdatedOrHotFixedProductionDeployments extends HealthMetric("OUTDATED_OR_HOT_FIXED_PRODUCTION_DEPLOYMENTS")
  case TestFailures                            extends HealthMetric("TEST_FAILURES"                               )
  case AccessibilityAssessmentViolations       extends HealthMetric("ACCESSIBILITY_ASSESSMENT_VIOLATIONS"         )
  case SecurityAssessmentAlerts                extends HealthMetric("SECURITY_ASSESSMENT_ALERTS"                  )


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
  given KeyWrites[HealthMetric] = KeyWrites.derived

  val mongoFormat: Format[TeamHealthMetricsHistory] =
    ( (__ \ "teamName").format[TeamName]
    ~ (__ \ "date"    ).format[LocalDate](MongoJavatimeFormats.localDateFormat)
    ~ (__ \ "metrics" ).format[Map[HealthMetric, Int]]
    )(TeamHealthMetricsHistory.apply, pt => Tuple.fromProductTyped(pt))

  val apiWrites: Writes[TeamHealthMetricsHistory] =
    ( (__ \ "teamName").write[TeamName]
    ~ (__ \ "date"    ).write[LocalDate]
    ~ (__ \ "metrics" ).write[Map[HealthMetric, Int]]
    )(pt => Tuple.fromProductTyped(pt))
