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
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.healthmetrics.model.{HealthMetric, HealthMetricTimelineCount, TeamHealthMetricsHistory, TeamName}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class TeamHealthMetricsRepositorySpec
  extends AnyWordSpec
     with Matchers
     with DefaultPlayMongoRepositorySupport[TeamHealthMetricsHistory]
     with IntegrationPatience:

  override val repository: TeamHealthMetricsRepository = TeamHealthMetricsRepository(mongoComponent)

  private val team1 = TeamName("team1")
  private val team2 = TeamName("team2")

  "getMaxDate" should:
    val metrics1 = TeamHealthMetricsHistory(team1, LocalDate.parse("2025-06-09"), Map.empty[HealthMetric, Int])
    val metrics2 = TeamHealthMetricsHistory(team1, LocalDate.parse("2025-06-10"), Map.empty[HealthMetric, Int])
    val metrics3 = TeamHealthMetricsHistory(team1, LocalDate.parse("2025-06-11"), Map.empty[HealthMetric, Int])

    "get the latest date" in:
      repository.collection.insertMany(Seq(metrics1, metrics2, metrics3)).toFuture().futureValue
      repository.getMaxDate().futureValue shouldBe Some(LocalDate.parse("2025-06-11"))

  "getHealthMetricTimelineCounts" should:
    val metrics1 = TeamHealthMetricsHistory(team1, LocalDate.parse("2025-05-09"), Map(HealthMetric.LatestBobbyWarnings -> 1, HealthMetric.ProductionBobbyWarnings -> 2))
    val metrics2 = TeamHealthMetricsHistory(team1, LocalDate.parse("2025-05-10"), Map(HealthMetric.LatestBobbyWarnings -> 1, HealthMetric.ProductionBobbyWarnings -> 2))
    val metrics3 = TeamHealthMetricsHistory(team1, LocalDate.parse("2025-05-11"), Map(HealthMetric.LatestBobbyWarnings -> 1, HealthMetric.ProductionBobbyWarnings -> 2))
    val metrics4 = TeamHealthMetricsHistory(team2, LocalDate.parse("2025-05-09"), Map(HealthMetric.LatestBobbyWarnings -> 1, HealthMetric.ProductionBobbyWarnings -> 2))

    "return health metrics counts for a specific team and metric, between given dates" in:
      repository.collection.insertMany(Seq(metrics1, metrics2, metrics3, metrics4)).toFuture().futureValue

      repository.getHealthMetricTimelineCounts(
        teamName     = team1
      , healthMetric = HealthMetric.LatestBobbyWarnings
      , from         = LocalDate.parse("2025-05-01")
      , to           = LocalDate.parse("2025-05-11")
      ).futureValue shouldBe Seq(
        HealthMetricTimelineCount(LocalDate.parse("2025-05-09"), 1)
      , HealthMetricTimelineCount(LocalDate.parse("2025-05-10"), 1)
      )

    "return no health metrics counts when no data between given dates" in:
      repository.collection.insertMany(Seq(metrics1, metrics2, metrics3, metrics4)).toFuture().futureValue

      repository.getHealthMetricTimelineCounts(
        teamName     = team1
      , healthMetric = HealthMetric.LatestBobbyWarnings
      , from         = LocalDate.parse("2025-05-12")
      , to           = LocalDate.parse("2025-05-13")
      ).futureValue shouldBe Seq.empty[HealthMetricTimelineCount]

    "return no health metrics counts when the from date is after the to date" in:
      repository.collection.insertMany(Seq(metrics1, metrics2, metrics3, metrics4)).toFuture().futureValue

      repository.getHealthMetricTimelineCounts(
        teamName     = team1
      , healthMetric = HealthMetric.LatestBobbyWarnings
      , from         = LocalDate.parse("2025-05-02")
      , to           = LocalDate.parse("2025-05-01")
      ).futureValue shouldBe Seq.empty[HealthMetricTimelineCount]
