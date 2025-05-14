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

///*
// * Copyright 2025 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.healthmetrics.persistence
//
//import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
//import play.api.libs.functional.syntax.toFunctionalBuilderOps
//import play.api.libs.json.{Format, Reads, Writes, __}
//import uk.gov.hmrc.healthmetrics.model.{HealthMetric, TeamName}
//import uk.gov.hmrc.mongo.MongoComponent
//import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
//import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
//
//import java.time.Instant
//import java.util.concurrent.TimeUnit
//import javax.inject.{Inject, Singleton}
//import scala.concurrent.ExecutionContext
//
//@Singleton
//class TeamHealthMetricsHistoryRepository @Inject()(
//  mongoComponent: MongoComponent
//)(using
//  ExecutionContext
//) extends PlayMongoRepository(
//  mongoComponent = mongoComponent,
//  collectionName = "teamHealthMetricsHistory",
//  domainFormat   = TeamHealthMetricsHistoryRepository.TeamHealthMetricHistory.format,
//  indexes        = Seq(
//                     IndexModel(Indexes.ascending("service")),
//                     IndexModel(Indexes.ascending("since")),
//                     IndexModel(Indexes.ascending("timestamp"), IndexOptions().expireAfter(90, TimeUnit.DAYS)),
//                     IndexModel(Indexes.ascending("environment")),
//                     IndexModel(Indexes.ascending("logType.logMetricId")),
//                   ),
//  extraCodecs    = Seq(Codecs.playFormatCodec(LogHistoryRepository.LogType.format))
//):
//
//  def find() = ???
//
//object TeamHealthMetricsHistoryRepository:
//  case class TeamHealthMetricHistory(
//    teamName : TeamName
//  , timestamp: Instant
//  , metrics  : Map[HealthMetric, Int]
//  )
//
//  object TeamHealthMetricHistory:
//    given Format[Instant]      = MongoJavatimeFormats.instantFormat
//    given Writes[HealthMetric] = HealthMetric.derived$Writes
//    given Reads[HealthMetric]  = HealthMetric.derived$Reads
//
//    val format: Format[TeamHealthMetricHistory] =
//      ( (__ \ "teamName" ).format[TeamName]
//      ~ (__ \ "timestamp").format[Instant]
//      ~ (__ \ "metrics"  ).format[Map[HealthMetric, Int]]
//    )(TeamHealthMetricHistory.apply, pt => Tuple.fromProductTyped(pt))