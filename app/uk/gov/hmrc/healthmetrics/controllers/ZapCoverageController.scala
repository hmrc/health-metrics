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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.healthmetrics.service.ZapCoverageService
import uk.gov.hmrc.healthmetrics.model.{ZapCoverageRequest, ZapCoverageResult}
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import play.api.Logging
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.{Action, ControllerComponents}

@Singleton
class ZapCoverageController @Inject()(
  cc                : ControllerComponents,
  zapCoverageService: ZapCoverageService
)(using
  ExecutionContext
) extends BackendController(cc) 
    with  Logging:

  private given Reads[ZapCoverageRequest] = ZapCoverageRequest.reads
  private given Writes[ZapCoverageResult] = ZapCoverageResult.writes

  def calculateZapCoverage: Action[ZapCoverageRequest] =
    Action.async(parse.json[ZapCoverageRequest]):
      implicit request =>
        zapCoverageService
          .calculateZapCoverage(request.body)
          .map(result => Ok(Json.toJson(result)))
