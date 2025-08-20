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

package uk.gov.hmrc.healthmetrics.persistence

import org.mongodb.scala.model.{Filters, FindOneAndReplaceOptions}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import org.mongodb.scala.model.{IndexModel, Indexes, IndexOptions}

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LastRunRepository @Inject() (
  mongoComponent: MongoComponent
)(
using ExecutionContext
) extends PlayMongoRepository[LastRunRepository.LastRun](
  mongoComponent = mongoComponent
, collectionName = "lastRun"
, domainFormat   = LastRunRepository.LastRun.mongoFormat
, indexes        = IndexModel(Indexes.ascending("lockId"), IndexOptions().unique(true)) :: Nil
, extraCodecs    = Seq.empty
):
  // we replace all the data for each call to updateLastWarningDate()
  override lazy val requiresTtlIndex = false

  def set(lockId: String, lastRunTime: Instant): Future[Unit] =
    collection
      .findOneAndReplace(
        filter      = Filters.eq("lockId", lockId)
      , replacement = LastRunRepository.LastRun(lockId, lastRunTime)
      , options     = FindOneAndReplaceOptions().upsert(true)
      )
      .headOption()
      .map(_ => ())

  def get(lockId: String): Future[Option[Instant]] =
    collection
      .find(Filters.eq("lockId", lockId))
      .map(_.lastRunTime)
      .headOption()

object LastRunRepository:
  case class LastRun(lockId: String, lastRunTime: Instant)

  object LastRun:
    val mongoFormat: Format[LastRun] =
      ( (__ \ "lockId"     ).format[String]
      ~ (__ \ "lastRunTime").format[Instant](MongoJavatimeFormats.Implicits.jatInstantFormat)
      )(LastRun.apply, pt => Tuple.fromProductTyped(pt))
