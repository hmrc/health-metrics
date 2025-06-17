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

package uk.gov.hmrc.healthmetrics.util

/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.{JsError, JsString, JsSuccess, KeyWrites, Reads, Writes}
import play.api.mvc.{PathBindable, QueryStringBindable}

import scala.util.Try

trait FromString { def asString: String }

// Kleisli[Either[String, _], String, T]
trait Parser[T] { def parse(s: String): Either[String, T] }

object Parser:
  def parser[T <: FromString](values: Array[T]): Parser[T] =
    (s: String) =>
      values
        .find(_.asString.equalsIgnoreCase(s))
        .toRight(s"Invalid value: \"$s\" - should be one of: ${values.map(_.asString).mkString(", ")}")

  @inline def apply[T](using instance: Parser[T]): Parser[T] = instance
end Parser

object FromStringEnum:
  extension (obj: Ordering.type)
    def derived[A <: scala.reflect.Enum]: Ordering[A] =
      Ordering.by(_.ordinal)

  extension (obj: Writes.type)
    def derived[A <: FromString]: Writes[A] =
      a => JsString(a.asString)

  extension (obj: Reads.type)
    def derived[A : Parser]: Reads[A] =
      _.validate[String]
        .flatMap(Parser[A].parse(_).fold(JsError(_), JsSuccess(_)))

  extension (obj: KeyWrites.type)
    // Ensures Map keys are serialised as strings, not arrays (Play's default for non-String keys)
    def derived[K <: FromString]: KeyWrites[K] =
      _.asString
end FromStringEnum

object Binders:
  given QueryStringBindable[java.time.LocalDate] =
    queryStringBindableFromString[java.time.LocalDate](
      s => Some(Try(java.time.LocalDate.parse(s)).toEither.left.map(_.getMessage)),
      _.toString
    )

  def queryStringBindableFromString[T](parse: String => Option[Either[String, T]], asString: T => String)(using strBinder: QueryStringBindable[String]): QueryStringBindable[T] =
    new QueryStringBindable[T]:
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, T]] =
        strBinder.bind(key, params) match
          case Some(Right(s)) if s.trim.nonEmpty => parse(s.trim)
          case _                                 => None

      override def unbind(key: String, value: T): String =
        asString(value)

    /** `summon[PathBindable[String]].transform` doesn't allow us to provide failures.
   * This function provides `andThen` semantics
   */
  def pathBindableFromString[T](parse: String => Either[String, T], asString: T => String)(using strBinder: PathBindable[String]): PathBindable[T] =
    new PathBindable[T]:
      override def bind(key: String, value: String): Either[String, T] =
        parse(value)

      override def unbind(key: String, value: T): String =
        asString(value)
