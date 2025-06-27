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

package uk.gov.hmrc.healthmetrics.persistence

import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Indexes.descending
import org.mongodb.scala.model.{Filters, IndexModel, Indexes, Sorts}
import org.mongodb.scala.model.Aggregates.{`match`, project, sort}
import play.api.libs.json.Format
import uk.gov.hmrc.healthmetrics.model.{HealthMetric, HealthMetricTimelineCount, TeamHealthMetricsHistory, TeamName}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamHealthMetricsRepository @Inject()(
  mongoComponent: MongoComponent
)(using
  ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent
, collectionName = "teamHealthMetrics"
, domainFormat   = TeamHealthMetricsHistory.mongoFormat
, indexes        = Seq(
                     IndexModel(Indexes.ascending("teamName"))
                   , IndexModel(Indexes.ascending("date"    ))
                   )
, extraCodecs    = Seq(
                     Codecs.playFormatCodec(summon[Format[TeamName]]             )
                   , Codecs.playFormatCodec(HealthMetricTimelineCount.mongoFormat)
                   )
):

  override lazy val requiresTtlIndex = false

  def getMaxDate(): Future[Option[LocalDate]] =
    collection
      .find()
      .sort(descending("date"))
      .limit(1)
      .first()
      .toFutureOption()
      .map(_.map(_.date))

  def insertMany(metrics: Seq[TeamHealthMetricsHistory]): Future[Unit] =
    collection.insertMany(metrics).toFuture().map(_ => ())

  def getHealthMetricTimelineCounts(
    teamName    : TeamName
  , healthMetric: HealthMetric
  , from        : LocalDate
  , to          : LocalDate
  ): Future[Seq[HealthMetricTimelineCount]] =
    collection
      .aggregate[HealthMetricTimelineCount]:
        Seq(
          `match`:
            Filters.and(
              Filters.equal("teamName", teamName)
            , Filters.gte("date", from)
            , Filters.lt("date", to)
            )
        , project:
            BsonDocument(
              "date"  -> 1
            , "count" -> s"$$metrics.${healthMetric.asString}"
            )
        , sort:
            Sorts.ascending("date")
        )
      .toFuture()
