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
import play.api.libs.json.{JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Reads, __}
import uk.gov.hmrc.healthmetrics.connector.ReleasesConnector.{Version, WhatsRunningWhere}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReleasesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext):
  import HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("releases-api")

  private given Reads[WhatsRunningWhere] = WhatsRunningWhere.reads

  def releases(
    teamName      : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  )(using HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    httpClientV2
      .get(url"$url/releases-api/whats-running-where?teamName=${teamName.map(_.asString)}&digitalService=${digitalService.map(_.asString)}")
      .execute[Seq[WhatsRunningWhere]]


object ReleasesConnector:
  case class Version(
    major   : Int,
    minor   : Int,
    patch   : Int,
    original: String
  ) extends Ordered[Version]:

    override def compare(other: Version): Int =
      summon[Ordering[Version]].compare(this, other)

    override def toString: String =
      original

  private object Version:
    given Ordering[Version] =
      Ordering.by: v =>
        (v.major, v.minor, v.patch)
        
    def apply(s: String): Version =
      val regex3 = """(\d+)\.(\d+)\.(\d+)(.*)""".r
      val regex2 = """(\d+)\.(\d+)(.*)""".r
      val regex1 = """(\d+)(.*)""".r
      s match
        case regex3(maj, min, patch, _) => Version(Integer.parseInt(maj), Integer.parseInt(min), Integer.parseInt(patch), s)
        case regex2(maj, min, _)        => Version(Integer.parseInt(maj), Integer.parseInt(min), 0                      , s)
        case regex1(patch, _)           => Version(0                    , 0                    , Integer.parseInt(patch), s)
        case _                          => Version(0                    , 0                    , 0                      , s)
    
    val reads: Reads[Version] = Reads:
      case JsString(s) => JsSuccess(Version(s))
      case JsObject(m) => m.get("original") match
                            case Some(JsString(s)) => JsSuccess(Version(s))
                            case _                 => JsError("Expected 'original' as a string")
      case _           => JsError("Expected a string or an object with 'original'")
  end Version
  
  case class WhatsRunningWhereVersion(
    environment: String,
    version    : Version,
  )
  
  private object WhatsRunningWhereVersion:
    val reads: Reads[WhatsRunningWhereVersion] =
      ( (__ \ "environment"  ).read[String]
      ~ (__ \ "versionNumber").read[Version](Version.reads)
      )(WhatsRunningWhereVersion.apply)
  
  
  case class WhatsRunningWhere(versions: List[WhatsRunningWhereVersion])
  
  object WhatsRunningWhere:
    val reads: Reads[WhatsRunningWhere] = 
      given Reads[WhatsRunningWhereVersion] = WhatsRunningWhereVersion.reads
      (__ \ "versions").read[List[WhatsRunningWhereVersion]]
        .map(WhatsRunningWhere.apply)