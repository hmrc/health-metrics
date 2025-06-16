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

package uk.gov.hmrc.healthmetrics

import play.api.mvc.QueryStringBindable
import play.api.libs.functional.syntax.*
import play.api.libs.json.{Format, Reads, __}
import uk.gov.hmrc.healthmetrics.util.{Binders, FromString, FromStringEnum, Parser}

package object model:

  // we're not using opaque types since they are not supported in PathBindable

  private trait StringAnyValUtils[T](fromString: String => T, toString: T => String):
    given Format[T] =
      Format.of[String].inmap(fromString, toString)

    given Ordering[T] =
      Ordering.by(toString(_).toLowerCase)
    
    given QueryStringBindable[T] =
      Binders.queryStringBindableFromString(
        s => Some(Right(fromString(s))),
        toString
      )
  end StringAnyValUtils
  
  type MetricFilter = TeamName | DigitalService

  case class TeamName(asString: String) extends AnyVal

  object TeamName extends StringAnyValUtils(TeamName.apply, _.asString):
    val nameReads: Reads[TeamName] =
      (__ \ "name").read[String].map(TeamName.apply)

  case class DigitalService(asString: String) extends AnyVal

  object DigitalService extends StringAnyValUtils(DigitalService.apply, _.asString):
    val reads: Reads[DigitalService] =
      Reads.of[String].map(DigitalService.apply)

  import FromStringEnum._

  given Parser[Environment] = Parser.parser(Environment.values)

  enum Environment(
    override val asString: String
  ) extends FromString
    derives Reads:
    case Production extends Environment(asString = "production")

  given Parser[SlugInfoFlag] = Parser.parser(SlugInfoFlag.values)

  enum SlugInfoFlag(
    override val asString: String
  ) extends FromString:
    case Latest      extends SlugInfoFlag(asString = "latest"    )
    case Production  extends SlugInfoFlag(asString = "production")

  given Parser[ShutterType] = Parser.parser(ShutterType.values)

  enum ShutterType(
    override val asString: String
  ) extends FromString
    derives Reads:
    case Frontend extends ShutterType(asString = "frontend")
    case Api      extends ShutterType(asString = "api"     )

  given Parser[ShutterStatusValue] = Parser.parser(ShutterStatusValue.values)

  enum ShutterStatusValue(
    override val asString: String
  ) extends FromString
    derives Reads:
    case Shuttered   extends ShutterStatusValue(asString = "shuttered"  )
    case Unshuttered extends ShutterStatusValue(asString = "unshuttered")

  given Parser[LogMetricId] = Parser.parser(LogMetricId.values)

  enum LogMetricId(
    override val asString: String
  ) extends FromString
    derives Reads:
    case ContainerKills   extends LogMetricId(asString = "container-kills"   )
    case NonIndexedQuery  extends LogMetricId(asString = "non-indexed-query" )
    case SlowRunningQuery extends LogMetricId(asString = "slow-running-query")
