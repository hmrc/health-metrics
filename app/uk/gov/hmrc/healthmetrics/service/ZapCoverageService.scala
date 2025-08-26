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
import uk.gov.hmrc.healthmetrics.model.{Environment, ServiceName, Version, ZapCoverageResult, ZapCoverageRequest}
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
    getPublicPaths(request.serviceName, request.version).map: pathsWithPrefixes =>
      // we do distinct because the proxied urls do not have GET/POST context so we
      // only assess the path and make the assumption that any GET/POST with the same
      // path will be part of a one question per page journey, and thus the POST will
      // have been made to progress through the journey
      val pathsWithRegex = pathsWithPrefixes.paths.distinct.map: path =>
        PathWithRegex(path, routePathToRegex(path))

      val pathsWithMatches = pathsWithRegex.map: pathWithRegex =>
        val matches = request.proxiedPaths.filter(pathWithRegex.regex.matches)
        ZapCoverageResult.PathWithMatches(pathWithRegex.path, matches)

      val coveredPaths   = pathsWithMatches.filter(_.matches.nonEmpty)
      val uncoveredPaths = pathsWithMatches.filter(_.matches.isEmpty).map(_.path)

      ZapCoverageResult(
        service            = request.serviceName.asString,
        version            = request.version.original,
        totalRoutes        = pathsWithRegex.length,
        coveredRoutes      = coveredPaths.length,
        coveragePercentage = if (pathsWithRegex.nonEmpty) then
                               BigDecimal(coveredPaths.length.toDouble / pathsWithRegex.length * 100)
                                 .setScale(2, BigDecimal.RoundingMode.HALF_UP)
                             else BigDecimal(0),
        matches            = coveredPaths,
        uncoveredPaths     = uncoveredPaths,
        publicPrefixes     = pathsWithPrefixes.publicPrefixes
      )

  private def routePathToRegex(routePath: String): Regex =
    val regexPattern =
      routePath
        .replaceAll("""([.^$+{}()\[\]|\\])""", """\\$1""") // escape special regex characters (except : and * which we'll handle)
        .replaceAll(""":([^/]+)""", """([^/]+)""")         // handle single path parameters (:param)
        .replaceAll("""\*([^/]+)""", """(.+)""")           // handle wildcard path parameters (*param)

    s"^$regexPattern(?:\\?.*)?(?:#.*)?$$".r // allow for query params and anchor tags to be picked up and matched

  private def getPublicPaths(service: ServiceName, version: Version)(using HeaderCarrier): Future[PathsWithPublicPrefixes] =
    for
      // TODO determine if frontend/admin-frontend/api, currently only frontend supported
      prodRoutes     <- serviceConfigsConnector.frontendRoutes(service, Environment.Production)
      appRoutes      <- serviceConfigsConnector.appRoutes(service, version)
    yield appRoutes match
      case Some(routes) =>
        // TODO handle where frontendRoutes.isRegex = true
        // e.g. https://github.com/hmrc/mdtp-frontend-routes/blob/108baca6a66c5a91708e532e24d24af6a8936f83/production/frontend-proxy-application-rules.conf#L57
        val publicPrefixes = prodRoutes.map(_.path)
        val filteredPaths =
          if publicPrefixes.nonEmpty then
            routes.paths.filter(path => publicPrefixes.exists(path.startsWith))
          else routes.paths  // no public prefixes found for prod, assess all routes

        PathsWithPublicPrefixes(filteredPaths, publicPrefixes)

      case None => throw RuntimeException(s"Unable to retrieve public paths for $service v$version")

case class PathWithRegex(path: String, regex: Regex)
case class PathsWithPublicPrefixes(paths: Seq[String], publicPrefixes: Seq[String])
