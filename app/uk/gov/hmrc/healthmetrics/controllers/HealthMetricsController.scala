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

package uk.gov.hmrc.healthmetrics.controllers

import play.api.Logging
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents, RequestHeader}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, HealthMetric, HealthMetricTimelineCount, LatestHealthMetrics, TeamName}
import uk.gov.hmrc.healthmetrics.persistence.TeamHealthMetricsRepository
import uk.gov.hmrc.healthmetrics.service.HealthMetricsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class HealthMetricsController @Inject()(
  cc                         : ControllerComponents
, healthMetricsService       : HealthMetricsService
, teamHealthMetricsRepository: TeamHealthMetricsRepository
)(using
  ExecutionContext
) extends BackendController(cc) 
    with  Logging:

  def latestTeamHealthMetrics(team: TeamName): Action[AnyContent] =
    Action.async: request =>
      given RequestHeader               = request
      given Writes[LatestHealthMetrics] = LatestHealthMetrics.writes
      healthMetricsService
        .generateHealthMetrics(team, java.time.LocalDate.now())
        .map: metrics =>
          Ok(Json.toJson(LatestHealthMetrics(metrics)))


  def latestDigitalServiceHealthMetrics(digitalService: DigitalService): Action[AnyContent] =
    Action.async: request =>
      given RequestHeader               = request
      given Writes[LatestHealthMetrics] = LatestHealthMetrics.writes
      healthMetricsService
        .generateHealthMetrics(digitalService, java.time.LocalDate.now())
        .map: metrics =>
          Ok(Json.toJson(LatestHealthMetrics(metrics)))

  def healthMetricsTimelineCounts(
    team        : TeamName
  , healthMetric: HealthMetric
  , from        : LocalDate
  , to          : LocalDate
  ): Action[AnyContent] =
     Action.async: request =>
       given Writes[HealthMetricTimelineCount] = HealthMetricTimelineCount.apiWrites
       teamHealthMetricsRepository
         .getHealthMetricTimelineCounts(team, healthMetric, from, to)
         .map: metrics =>
           Ok(Json.toJson(metrics))
