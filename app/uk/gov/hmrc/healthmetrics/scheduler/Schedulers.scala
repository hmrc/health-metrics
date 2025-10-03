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

package uk.gov.hmrc.healthmetrics.scheduler

import org.apache.pekko.actor.ActorSystem

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, ScheduledLockService}
import uk.gov.hmrc.healthmetrics.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.healthmetrics.model.TeamHealthMetricsHistory
import uk.gov.hmrc.healthmetrics.service.*
import uk.gov.hmrc.healthmetrics.persistence.{LastRunRepository, TeamHealthMetricsRepository}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import java.time.temporal.ChronoUnit

@Singleton
class Schedulers @Inject()(
  configuration                           : Configuration
, healthMetricsService                    : HealthMetricsService
, teamsAndRepositoriesConnector           : TeamsAndRepositoriesConnector
, callingDeprecatedServiceNotifierService : CallingDeprecatedServiceNotifierService
, productionBobbyNotifierService          : ProductionBobbyNotifierService
, productionVersionNotifierService        : ProductionVersionNotifierService
, outdatedDeploymentNotifierService       : OutdatedDeploymentNotifierService
, productionVulnerabilitiesNotifierService: ProductionVulnerabilitiesNotifierService
, upcomingBobbyNotifierService            : UpcomingBobbyNotifierService
, inactiveTestRepoService                 : InactiveTestRepoService
, mongoLockRepository                     : MongoLockRepository
, lastRunRepository                       : LastRunRepository
, teamHealthMetricsRepository             : TeamHealthMetricsRepository
, timestampSupport                        : TimestampSupport
)(using
  actorSystem         : ActorSystem
, applicationLifecycle: ApplicationLifecycle
, ec                  : ExecutionContext
) extends Logging:

  given HeaderCarrier = HeaderCarrier()

  private def scheduleWithLock(label: String, key: String)(f: SchedulerConfig => Future[Unit]): Unit =

    val schedulerConfig: SchedulerConfig =
      SchedulerConfig(configuration, label = label, key = key)

    val schedulerLock: ScheduledLockService =
      ScheduledLockService(
        lockRepository    = mongoLockRepository,
        lockId            = key,
        timestampSupport  = timestampSupport,
        schedulerInterval = schedulerConfig.interval
      )

    SchedulerUtils.scheduleWithLock(label, schedulerConfig, schedulerLock)(f(schedulerConfig))

  scheduleWithLock("Team Health Metrics", "team-health-metrics"): _ =>
    import cats.implicits.*
    for
      latestRecordedDate <- teamHealthMetricsRepository.getMaxDate()
      currentDate        =  java.time.LocalDate.now()
      result             <- latestRecordedDate match
                              case Some(date) if date == currentDate
                                              => logger.debug(s"Metrics already updated for today ($date), skipping update.")
                                                 Future.unit
                              case _          => logger.info("Updating team health metrics")
                                                 for
                                                   teams         <- teamsAndRepositoriesConnector.allTeams()
                                                   latestMetrics <- teams.foldLeftM(Seq.empty[TeamHealthMetricsHistory]): (acc, team) =>
                                                                      healthMetricsService.generateHealthMetrics(team.name, currentDate).map: metrics =>
                                                                        acc :+ TeamHealthMetricsHistory(
                                                                          teamName = team.name
                                                                        , date     = currentDate
                                                                        , metrics  = metrics
                                                                        )
                                                   _             <- teamHealthMetricsRepository.insertMany(latestMetrics)
                                                 yield logger.info("Finished updating team health metrics")
    yield result

  scheduleWithLock("Calling Deprecated Service", "calling-deprecated-service-notifier"): schedulerConfig =>
    run(schedulerConfig):
      callingDeprecatedServiceNotifierService.notify()

  scheduleWithLock("Production Bobby Notifier", "production-bobby-notifier"): schedulerConfig =>
    run(schedulerConfig):
      productionBobbyNotifierService.notify()

  scheduleWithLock("Production Version Notifier", "production-version-notifier"): schedulerConfig =>
    run(schedulerConfig):
      productionVersionNotifierService.notify(Instant.now())

  scheduleWithLock("Production Vulnerabilities Notifier", "production-vulnerabilities-notifier"): schedulerConfig =>
    run(schedulerConfig):
      productionVulnerabilitiesNotifierService.notify()

  scheduleWithLock("Production Vulnerabilities Notifier", "upcoming-bobby-notifier"): schedulerConfig =>
    run(schedulerConfig):
      upcomingBobbyNotifierService.notify(Instant.now())

  scheduleWithLock("Outdated Deployment Notifier", "outdated-deployment-notifier"): schedulerConfig =>
    run(schedulerConfig):
      outdatedDeploymentNotifierService.notify(Instant.now())
  
  scheduleWithLock("Inactive Test Repositories Notifier", "inactive-test-repositories-notifier"): schedulerConfig =>
    run(schedulerConfig):
      inactiveTestRepoService.notify

  private def run(schedulerConfig: SchedulerConfig)(f: => Future[Unit]): Future[Unit] =
    val now   = Instant.now()
    val after = now.truncatedTo(ChronoUnit.DAYS).minusSeconds(schedulerConfig.runEvery.toSeconds)

    if DateAndTimeOps.isInWorkingHours(now) then
      lastRunRepository
        .get(schedulerConfig.lockId)
        .flatMap:
          case Some(last) if last.isAfter(after) =>
            logger.info(s"Not running ${schedulerConfig.label} Scheduler - waiting till e: $last is after $after and inside working hours")
            Future.unit
          case oLast =>
            logger.info(oLast.fold(s"Running ${schedulerConfig.label} Scheduler for the first time")(d => s"Running ${schedulerConfig.label} Scheduler. Last run date was $d"))
            for
              _ <- f
              _ <- lastRunRepository.set(schedulerConfig.lockId, now)
            yield ()
    else
      logger.info(s"Not running ${schedulerConfig.label} Scheduler. Out of hours")
      Future.unit
