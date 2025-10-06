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

package uk.gov.hmrc.healthmetrics.connector

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.healthmetrics.model.TeamName

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SlackNotificationsConnector @Inject()(
  configuration : Configuration
, servicesConfig: ServicesConfig
, httpClientV2  : HttpClientV2
)(using
  ec: ExecutionContext
) extends Logging:

  import SlackNotificationsConnector._
  import HttpReads.Implicits._

  private val baseUrl         = servicesConfig.baseUrl("slack-notifications")
  private val token           = servicesConfig.getString("microservice.services.slack-notifications.authToken")
  private val channelOverride = configuration.getOptional[String]("microservice.services.slack-notifications.channelOverride")

  def sendMessage(request: Request)(using hc: HeaderCarrier): Future[Response] =
    given Writes[Request] = Request.writes
    given Reads[Response] = Response.reads
    httpClientV2
      .post(url"$baseUrl/slack-notifications/v2/notification")
      .withBody:
        Json.toJson(channelOverride.fold(request)(c => request.copy(channelLookup = ChannelLookup.ByChannels(Seq(c)))))
      .setHeader("Authorization" -> token)
      .execute[Response]
      .recoverWith:
        case NonFatal(ex) =>
          logger.error(s"Unable to notify ${request.channelLookup} on Slack", ex)
          Future.failed(ex)


object SlackNotificationsConnector:

  final case class Error(
    code   : String,
    message: String
  )

  final case class Response(
    errors: List[Error]
  )

  object Response:
    val reads: Reads[Response] =
      given sneReads: Reads[Error] =
        ( (__ \ "code"   ).read[String]
        ~ (__ \ "message").read[String]
        )(Error.apply _)

      (__ \ "errors")
        .readWithDefault[List[Error]](List.empty)
        .map(Response.apply)

  enum ChannelLookup:
    case ByChannels  (slackChannels: Seq[String]) extends ChannelLookup
    case ByGithubTeam(teamName     : TeamName   ) extends ChannelLookup

  object ChannelLookup:
    object ByChannel:
      val writes: Writes[ByChannels] =
        cl => Json.obj("by" -> "slack-channel", "slackChannels" -> cl.slackChannels)

    object ByGithubTeam:
      given writes: Writes[ByGithubTeam] =
        cl => Json.obj("by" -> "github-team", "teamName" -> cl.teamName)

    val writes: Writes[ChannelLookup] =
      case lookup: ByChannels   => Json.toJson(lookup)(ByChannel.writes)
      case lookup: ByGithubTeam => Json.toJson(lookup)(ByGithubTeam.writes)

  final case class Request(
    channelLookup  : ChannelLookup,
    displayName    : String,
    emoji          : String,
    text           : String,
    blocks         : Seq[JsValue],
    callbackChannel: Option[String] = None
  )

  object Request:
    val writes: Writes[Request] =
      ( (__ \ "channelLookup"  ).write[ChannelLookup](ChannelLookup.writes)
      ~ (__ \ "displayName"    ).write[String]
      ~ (__ \ "emoji"          ).write[String]
      ~ (__ \ "text"           ).write[String]
      ~ (__ \ "blocks"         ).write[Seq[JsValue]]
      ~ (__ \ "callbackChannel").writeNullable[String]
      )(o => Tuple.fromProductTyped(o))

  def withDivider(messages: Seq[JsValue]): Seq[JsValue] =
    Json.parse("""{"type": "divider"}"""") +: messages

  def mrkdwnBlock(message: String): JsValue =
    Json.parse:
      s"""{
        "type": "section",
        "text": {
          "type": "mrkdwn",
          "text": "$message"
        }
      }"""
