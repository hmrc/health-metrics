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

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.mvc.{PathBindable, QueryStringBindable}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsError, JsObject, JsString, JsSuccess, JsValue}

package object model:

  // we're not using opaque types since they are not supported in PathBindable

  private trait StringAnyValUtils[T](fromString: String => T, toString: T => String):
    given Format[T] =
      Format.of[String].inmap(fromString, toString)

    given Ordering[T] =
      Ordering.by(toString(_).toLowerCase)

//    given PathBindable[T] =
//      Binders.pathBindableFromString(
//        s => Right(fromString(s)),
//        toString
//      )
//
//    given QueryStringBindable[T] =
//      Binders.queryStringBindableFromString(
//        s => Some(Right(fromString(s))),
//        toString
//      )
//
//    given Formatter[T] =
//      Binders.formFormatFromString(
//        s => Right(fromString(s)),
//        toString
//      )

  case class TeamName(asString: String) extends AnyVal
  
  object TeamName extends StringAnyValUtils(TeamName.apply, _.asString)
  
  case class DigitalService(asString: String) extends AnyVal

  object DigitalService extends StringAnyValUtils(DigitalService.apply, _.asString)
