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

import play.api.libs.json.{Writes, __}
import play.api.libs.functional.syntax.*

case class ZapCoverageResult(
  totalRoutes       : Int,
  coveredRoutes     : Int,
  coveragePercentage: BigDecimal,
  matches           : Seq[ZapCoverageResult.PathWithMatches],
  uncoveredPaths    : Seq[String],
  publicPrefixes    : Seq[String]
)

object ZapCoverageResult:
  case class PathWithMatches(
    path: String,
    matches: Seq[String]
  )

  given Writes[PathWithMatches] =
    ( (__ \ "path"   ).write[String]
    ~ (__ \ "matches").write[Seq[String]]
    )(pwm => Tuple.fromProductTyped(pwm))

  val writes: Writes[ZapCoverageResult] =
    ( (__ \ "totalRoutes"       ).write[Int]
    ~ (__ \ "coveredRoutes"     ).write[Int]
    ~ (__ \ "coveragePercentage").write[BigDecimal]
    ~ (__ \ "matches"           ).write[Seq[PathWithMatches]]
    ~ (__ \ "uncoveredPaths"    ).write[Seq[String]]
    ~ (__ \ "publicPrefixes"    ).write[Seq[String]]
    )(zcr => Tuple.fromProductTyped(zcr))
