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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.healthmetrics.connector.ServiceConfigsConnector
import uk.gov.hmrc.healthmetrics.connector.ServiceConfigsConnector.{AppRoutes, FrontendRoute}
import uk.gov.hmrc.healthmetrics.model.{Environment, ServiceName, Version, ZapCoverageRequest, ZapCoverageResult}
import uk.gov.hmrc.healthmetrics.model.ZapCoverageResult.PathWithMatches

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

class ZapCoverageServiceSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with MockitoSugar:

  "ZapCoverageService.calculateZapCoverage" should:
    "evaluate coverage for all public routes" in new Setup:
      when(serviceConfigsConnector.frontendRoutes(any[ServiceName], eqTo(Environment.Production))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(FrontendRoute("/test-service", isRegex = false))))

      when(serviceConfigsConnector.appRoutes(any[ServiceName], any[Version])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Some(
            AppRoutes(
              service = ServiceName("test-service"),
              version = Version("0.1.0"),
              paths   = Seq(
                "/test-service/home",                 // simple path
                "/test-service/users/:userId",        // single param
                "/test-service/users/:userId/edit",   // param mid path
                "/test-service/reports/:year/:month", // multiple params
                "/test-service/files/*path",          // path param
                "/test-service/search",               // will have query params
                "/test-service/dashboard",            // won't be hit
                "/test-service/faq",                  // won't be hit
                "/internal/callback"                  // not public so won't be counted for coverage stats
              )
            )
          )
        ))

      val request =
        ZapCoverageRequest(
          serviceName  = ServiceName("test-service"),
          version      = Version("0.1.0"),
          proxiedPaths = Seq(
            "/test-service/home",
            "/test-service/users/john.doe",
            "/test-service/users/jane.doe",
            "/test-service/users/jane.doe/edit",
            "/test-service/reports/2024/12",
            "/test-service/reports/2024/11",
            "/test-service/files/path/to/some/file.pdf",
            "/test-service/search?userId=john.doe",
            "/test-service/search?userId=jane.doe"
          )
        )

      val expected =
        ZapCoverageResult(
          service            = "test-service",
          version            = "0.1.0",
          totalRoutes        = 8,
          coveredRoutes      = 6,
          coveragePercentage = BigDecimal(75.0),
          matches            = Seq(
            PathWithMatches(path = "/test-service/home",                 matches = Seq("/test-service/home")),
            PathWithMatches(path = "/test-service/users/:userId",        matches = Seq("/test-service/users/john.doe", "/test-service/users/jane.doe")),
            PathWithMatches(path = "/test-service/users/:userId/edit",   matches = Seq("/test-service/users/jane.doe/edit")),
            PathWithMatches(path = "/test-service/reports/:year/:month", matches = Seq("/test-service/reports/2024/12", "/test-service/reports/2024/11")),
            PathWithMatches(path = "/test-service/files/*path",          matches = Seq("/test-service/files/path/to/some/file.pdf")),
            PathWithMatches(path = "/test-service/search",               matches = Seq("/test-service/search?userId=john.doe", "/test-service/search?userId=jane.doe")),
          ),
          uncoveredPaths     = Seq(
            "/test-service/dashboard",
            "/test-service/faq"
          ),
          publicPrefixes = Seq("/test-service")
        )

      private given HeaderCarrier = HeaderCarrier()

      service.calculateZapCoverage(request).futureValue shouldBe expected

  trait Setup:
    val serviceConfigsConnector = mock[ServiceConfigsConnector]

    val service = ZapCoverageService(serviceConfigsConnector)
