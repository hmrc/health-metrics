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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.healthmetrics.connector.{SlackNotificationsConnector, TeamsAndRepositoriesConnector, VulnerabilitiesConnector}
import uk.gov.hmrc.healthmetrics.model.{RepoName, SlugInfoFlag, TeamName}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters.*

class ProductionVulnerabilitiesNotifierServiceSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with MockitoSugar:

  "ProductionVulnerabilitiesNotifierService.notify" should:
    "notify owning teams when action required vulnerabilities are in production" in new Setup:
      when(vulnerabilitiesConnector.vulnerabilitySummaries(
        any(), any(), eqTo(Some(SlugInfoFlag.Production.asString))
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(vulnerabilityWithOwningTeams))

      when(teamsAndRepositoriesConnector.allRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(repositoriesWithOwningTeams))

      when(slackNotificationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify().futureValue

      val captor: ArgumentCaptor[SlackNotificationsConnector.Request] = ArgumentCaptor.forClass(classOf[SlackNotificationsConnector.Request])
      verify(slackNotificationsConnector, times(2))
        .sendMessage(captor.capture())(using any[HeaderCarrier])

      val capturedRequests: List[SlackNotificationsConnector.Request] = captor.getAllValues.asScala.toList
      val notifiedTeams: List[TeamName] = capturedRequests.map(req =>
        req.channelLookup match
          case SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName) => teamName
          case _ => fail("Unexpected channel lookup type")
      )

      // Should notify owning teams, NOT the vulnerability teams (team1, team2)
      notifiedTeams.toSet should contain only (TeamName("owning-team-alpha"), TeamName("owning-team-beta"))

    "fall back to vulnerability teams when no owning teams exist" in new Setup:
      when(vulnerabilitiesConnector.vulnerabilitySummaries(
        any(), any(), eqTo(Some(SlugInfoFlag.Production.asString))
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(vulnerabilityWithoutOwningTeams))

      when(teamsAndRepositoriesConnector.allRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(repositoriesWithoutOwningTeams))

      when(slackNotificationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify().futureValue

      val captor: ArgumentCaptor[SlackNotificationsConnector.Request] = ArgumentCaptor.forClass(classOf[SlackNotificationsConnector.Request])
      verify(slackNotificationsConnector, times(1))
        .sendMessage(captor.capture())(using any[HeaderCarrier])

      val capturedRequests: List[SlackNotificationsConnector.Request] = captor.getAllValues.asScala.toList
      val notifiedTeams: List[TeamName] = capturedRequests.map(req =>
        req.channelLookup match
          case SlackNotificationsConnector.ChannelLookup.ByGithubTeam(teamName) => teamName
          case _ => fail("Unexpected channel lookup type")
      )

      // Should fall back to vulnerability team when no owning teams exist
      notifiedTeams.toSet should contain only TeamName("vulnerability-team")

    "skip services with no owning teams and no vulnerability teams" in new Setup:
      when(vulnerabilitiesConnector.vulnerabilitySummaries(
        any(), any(), eqTo(Some(SlugInfoFlag.Production.asString))
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(vulnerabilityWithNoTeams))

      when(teamsAndRepositoriesConnector.allRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(repositoriesWithoutOwningTeams))

      when(slackNotificationsConnector.sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(SlackNotificationsConnector.Response(List.empty)))

      service.notify().futureValue

      // Should not send any notifications
      verify(slackNotificationsConnector, times(0))
        .sendMessage(any[SlackNotificationsConnector.Request])(using any[HeaderCarrier])

  case class Setup():
    given HeaderCarrier = HeaderCarrier()

    // Test 1: Services with owning teams - these should be notified
    val vulnerabilityWithOwningTeams: Seq[VulnerabilitiesConnector.DistinctVulnerability] = Seq(
      VulnerabilitiesConnector.DistinctVulnerability(
        vulnerableComponentName = "component-alpha",
        vulnerableComponentVersion = "1.0.0",
        id = "CVE-7357",
        occurrences = Seq(
          VulnerabilitiesConnector.VulnerabilityOccurrence(
            vulnerableComponentName    = "occ",
            vulnerableComponentVersion = "0.1.0",
            componentPathInSlug        = "/test/path",
            teams                      = Seq(TeamName("team1")),  // This team should NOT be notified (owning teams take precedence)
            service                    = "service-with-owners"
          )
        )
      ),
      VulnerabilitiesConnector.DistinctVulnerability(
        vulnerableComponentName = "component-beta",
        vulnerableComponentVersion = "2.0.0",
        id = "CVE-1337",
        occurrences = Seq(
          VulnerabilitiesConnector.VulnerabilityOccurrence(
            vulnerableComponentName    = "lib",
            vulnerableComponentVersion = "1.0.0",
            componentPathInSlug        = "/a/b/c",
            teams                      = Seq(TeamName("team2")),  // This team should NOT be notified
            service                    = "another-service-with-owners"
          )
        )
      )
    )

    // Test 1: Repositories with owning teams
    val repositoriesWithOwningTeams: Seq[TeamsAndRepositoriesConnector.Repo] = Seq(
      TeamsAndRepositoriesConnector.Repo(
        repoName      = RepoName("service-with-owners"),
        teamNames     = Seq(TeamName("team1")),
        endOfLifeDate = None,
        owningTeams   = Seq(TeamName("owning-team-alpha"), TeamName("owning-team-beta"))
      ),
      TeamsAndRepositoriesConnector.Repo(
        repoName      = RepoName("another-service-with-owners"),
        teamNames     = Seq(TeamName("team2")),
        endOfLifeDate = None,
        owningTeams   = Seq(TeamName("owning-team-alpha"))
      ),
      TeamsAndRepositoriesConnector.Repo(
        repoName      = RepoName("service-not-vulnerable"),
        teamNames     = Seq(TeamName("team3")),
        endOfLifeDate = None,
        owningTeams   = Seq(TeamName("owning-team-gamma"))  // This team should NOT be notified (no vulnerabilities)
      )
    )

    // Test 2: Services without owning teams - should fall back to vulnerability teams
    val vulnerabilityWithoutOwningTeams: Seq[VulnerabilitiesConnector.DistinctVulnerability] = Seq(
      VulnerabilitiesConnector.DistinctVulnerability(
        vulnerableComponentName = "component-gamma",
        vulnerableComponentVersion = "3.0.0",
        id = "CVE-9999",
        occurrences = Seq(
          VulnerabilitiesConnector.VulnerabilityOccurrence(
            vulnerableComponentName    = "legacy-lib",
            vulnerableComponentVersion = "2.0.0",
            componentPathInSlug        = "/legacy/path",
            teams                      = Seq(TeamName("vulnerability-team")),  // Should be notified when no owning teams
            service                    = "legacy-service"
          )
        )
      )
    )

    // Test 2: Repositories without owning teams (empty sequence)
    val repositoriesWithoutOwningTeams: Seq[TeamsAndRepositoriesConnector.Repo] = Seq(
      TeamsAndRepositoriesConnector.Repo(
        repoName      = RepoName("legacy-service"),
        teamNames     = Seq(TeamName("legacy-team")),
        endOfLifeDate = None,
        owningTeams   = Seq.empty  // No owning teams, should fall back to vulnerability teams
      )
    )

    // Test 3: Services with no teams at all
    val vulnerabilityWithNoTeams: Seq[VulnerabilitiesConnector.DistinctVulnerability] = Seq(
      VulnerabilitiesConnector.DistinctVulnerability(
        vulnerableComponentName = "component-delta",
        vulnerableComponentVersion = "4.0.0",
        id = "CVE-5555",
        occurrences = Seq(
          VulnerabilitiesConnector.VulnerabilityOccurrence(
            vulnerableComponentName    = "orphan-lib",
            vulnerableComponentVersion = "3.0.0",
            componentPathInSlug        = "/orphan/path",
            teams                      = Seq.empty,  // No vulnerability teams
            service                    = "orphan-service"
          )
        )
      )
    )

    val vulnerabilitiesConnector: VulnerabilitiesConnector = mock[VulnerabilitiesConnector]
    val slackNotificationsConnector: SlackNotificationsConnector = mock[SlackNotificationsConnector]
    val teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]

    val service = new ProductionVulnerabilitiesNotifierService(
      vulnerabilitiesConnector,
      slackNotificationsConnector,
      teamsAndRepositoriesConnector
    )

