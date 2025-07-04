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

package uk.gov.hmrc.healthmetrics.connector

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.healthmetrics.connector.ShutterConnector.ShutterState
import uk.gov.hmrc.healthmetrics.model.{DigitalService, Environment, MetricFilter, ShutterStatusValue, ShutterType, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterConnector @Inject() (
  httpClientV2 : HttpClientV2,
  serviceConfig: ServicesConfig
)(using
  ExecutionContext
):
  import HttpReads.Implicits._

  private val url: String = 
    serviceConfig.baseUrl("shutter-api")
    
  def getShutterStates(
    st          : ShutterType
  , env         : Environment
  , metricFilter: MetricFilter
  )(using
    HeaderCarrier
  ): Future[Seq[ShutterState]] =
     given Reads[ShutterState] = ShutterState.reads

     val params: MetricFilter => Map[String, String] =
      case TeamName(name)       => Map("teamName"       -> name)
      case DigitalService(name) => Map("digitalService" -> name)

     httpClientV2
      .get(url"$url/shutter-api/${env.asString}/${st.asString}/states?${params(metricFilter)}")
      .execute[Seq[ShutterState]] 

    
object ShutterConnector:
  case class ShutterState(
    shutterType: ShutterType
  , status     : ShutterStatusValue
  )

  object ShutterState:
    val reads: Reads[ShutterState] =
      ( (__ \ "type"             ).read[ShutterType]
      ~ (__ \ "status" \ "value" ).read[ShutterStatusValue]
      )(ShutterState.apply)
