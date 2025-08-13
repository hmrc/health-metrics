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

package uk.gov.hmrc.healthmetrics.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.healthmetrics.model.{Environment, ZapCoverageResult, ZapCoverageRequest}
import uk.gov.hmrc.healthmetrics.connector.ServiceConfigsConnector
import scala.util.matching.Regex
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logging

@Singleton
class ZapCoverageService @Inject()(
  serviceConfigsConnector: ServiceConfigsConnector
)(using
  ec: ExecutionContext
) extends Logging:

  def calculateZapCoverage(request: ZapCoverageRequest)(using HeaderCarrier): Future[ZapCoverageResult] =
    getPublicPaths(request.serviceName, request.version).map: paths =>
      val pathsWithRegex = paths.distinct.map: path =>
        PathWithRegex(path, routePathToRegex(path))
      
      val pathsWithMatches = pathsWithRegex.map: pathWithRegex =>
        val matches = request.proxiedPaths.filter(pathWithRegex.regex.matches)
        ZapCoverageResult.PathWithMatches(pathWithRegex.path, matches)
      
      val coveredPaths   = pathsWithMatches.filter(_.matches.nonEmpty)
      val uncoveredPaths = pathsWithMatches.filter(_.matches.isEmpty).map(_.path)

      ZapCoverageResult(
        totalRoutes        = pathsWithRegex.length,
        coveredRoutes      = coveredPaths.length,
        coveragePercentage = BigDecimal(coveredPaths.length.toDouble / pathsWithRegex.length * 100)
                               .setScale(2, BigDecimal.RoundingMode.HALF_UP),
        matches            = coveredPaths,
        uncoveredPaths     = uncoveredPaths
      )

  private def routePathToRegex(routePath: String): Regex =
    val regexPattern =
      routePath
        .replaceAll("""([.^$+{}()\[\]|\\])""", """\\$1""") // escape special regex characters (except : and * which we'll handle)
        .replaceAll(""":([^/]+)""", """([^/]+)""")         // handle single path parameters (:param)
        .replaceAll("""\*([^/]+)""", """(.+)""")           // handle wildcard path parameters (*param)

    s"^$regexPattern(?:\\?.*)?(?:#.*)?$$".r // allow for query params and anchor tags to be picked up and matched

  private def getPublicPaths(service: String, version: String)(using HeaderCarrier): Future[Seq[String]] =
    for
      prodRoutes     <- serviceConfigsConnector.frontendRoutes(service, Environment.Production)
      appRoutes      <- serviceConfigsConnector.appRoutes(service, version)
    yield appRoutes match
      case Some(routes) =>
        //TODO deal with regex frontend routes
        val publicPrefixes = prodRoutes.map(_.path)
        routes.paths.filter: path =>
          publicPrefixes.exists(prefix => path.startsWith(prefix))
      case None => throw RuntimeException(s"Unable to retrieve public paths for $service v$version")

case class PathWithRegex(path: String, regex: Regex)
