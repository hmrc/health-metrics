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

package uk.gov.hmrc.healthmetrics.scheduler

import cats.syntax.all.*
import org.apache.pekko.actor.ActorSystem
import play.api.{Configuration, Logger}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, ScheduledLockService}
import uk.gov.hmrc.healthmetrics.service.HealthMetricsService
import uk.gov.hmrc.healthmetrics.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.healthmetrics.model
import uk.gov.hmrc.healthmetrics.model.{TeamHealthMetricsHistory, TeamName}
import uk.gov.hmrc.healthmetrics.persistence.TeamHealthMetricsRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HealthMetricsScheduler @Inject()(
  configuration                : Configuration
, lockRepository               : MongoLockRepository
, timestampSupport             : TimestampSupport
, healthMetricsService         : HealthMetricsService
, teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, teamHealthMetricsRepository  : TeamHealthMetricsRepository
)(using
  ActorSystem
, ApplicationLifecycle
, ExecutionContext
) extends SchedulerUtils:

  override val logger = Logger(getClass)

  private given HeaderCarrier = HeaderCarrier()

  private val schedulerConfig: SchedulerConfig =
    SchedulerConfig(configuration, "scheduler.metrics")

  scheduleWithLock(
    label           = "Team Health Metrics Scheduler",
    schedulerConfig = schedulerConfig,
    lock            = ScheduledLockService(lockRepository, "metrics-scheduler", timestampSupport, schedulerConfig.interval)
  ):
    for
      latestRecordedDate <- teamHealthMetricsRepository.getMaxDate
      currentDate        =  java.time.LocalDate.now()
      result             <- latestRecordedDate match
                              case Some(date) if date == currentDate
                                              => logger.debug(s"Metrics already updated for today ($date), skipping update.")
                                                 Future.unit
                              case _          => logger.info("Updating team health metrics")
                                                 for
                                                   teams         <- teamsAndRepositoriesConnector.allTeams()
                                                   latestMetrics <- teams.foldLeftM(Seq.empty[TeamHealthMetricsHistory]): (acc, teamName: TeamName) =>
                                                                      healthMetricsService.generateHealthMetrics(teamName, currentDate).map: metrics =>
                                                                        acc :+ TeamHealthMetricsHistory(
                                                                          teamName = teamName
                                                                        , date     = currentDate
                                                                        , metrics  = metrics
                                                                        )
                                                   _             <- teamHealthMetricsRepository.insertMany(latestMetrics)
                                                 yield logger.info("Finished updating team health metrics")
    yield result
