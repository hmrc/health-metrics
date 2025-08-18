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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.healthmetrics.connector.{ServiceDependenciesConnector, SlackNotificationsConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.healthmetrics.model.{RepoName, SlugInfoFlag, TeamName}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.temporal.ChronoUnit.DAYS

class ProductionBobbyNotifierServiceSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with MockitoSugar:

  "ProductionBobbyNotifierService.notify" should:
    "notify teams when bobby violations exist" in new Setup:
      when(serviceDependenciesConnector.bobbyReports(metricFilter = any(), eqTo(SlugInfoFlag.Production))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          ServiceDependenciesConnector.BobbyReport(RepoName("repo-1"), violations) ::
          ServiceDependenciesConnector.BobbyReport(RepoName("repo-2"), violations) ::
          Nil
        ))

      when(teamsAndReposConnector.allRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          TeamsAndRepositoriesConnector.Repo(repoName = RepoName("repo-1"), teamNames = Seq(TeamName("Team 1"))                    ,  endOfLifeDate = None) ::
          TeamsAndRepositoriesConnector.Repo(repoName = RepoName("repo-2"), teamNames = Seq(TeamName("Team 1"), TeamName("Team 2")),  endOfLifeDate = None) ::
          Nil
        ))

      when(slackNotificationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify().futureValue

      verify(slackNotificationsConnector, times(2)) // one message per team
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])


  case class Setup():
    given HeaderCarrier = HeaderCarrier()

    val violations = Seq(
      ServiceDependenciesConnector.BobbyReport.Violation(
        from   = java.time.LocalDate.now().minus(1L, DAYS),
        exempt = false
      )
    )

    val teamsAndReposConnector       = mock[TeamsAndRepositoriesConnector]
    val serviceDependenciesConnector = mock[ServiceDependenciesConnector]
    val slackNotificationsConnector  = mock[SlackNotificationsConnector]

    val service = new ProductionBobbyNotifierService(teamsAndReposConnector, serviceDependenciesConnector, slackNotificationsConnector)
