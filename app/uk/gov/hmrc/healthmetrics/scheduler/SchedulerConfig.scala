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

import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

case class SchedulerConfig(
  label        : String
, lockId       : String
, enabledKey   : String
, enabled      : Boolean
, interval     : FiniteDuration
, initialDelay : FiniteDuration
, runEvery     : FiniteDuration
)

object SchedulerConfig:
  def apply(configuration: Configuration, label: String, key: String): SchedulerConfig =
    SchedulerConfig(
      label        = label
    , lockId       = key
    , enabledKey   = s"scheduler.$key.enabled"
    , enabled      = configuration.get[Boolean       ](s"scheduler.$key.enabled")
    , interval     = configuration.get[FiniteDuration](s"scheduler.$key.interval")
    , initialDelay = configuration.get[FiniteDuration](s"scheduler.$key.initialDelay")
    , runEvery     = configuration.get[FiniteDuration](s"scheduler.$key.runEvery")
    )
