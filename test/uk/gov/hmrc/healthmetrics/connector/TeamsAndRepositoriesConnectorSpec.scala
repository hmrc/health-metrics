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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, stubFor, urlEqualTo}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.healthmetrics.connector.TeamsAndRepositoriesConnector.{BuildData, JenkinsJob, TestJobResults}
import uk.gov.hmrc.healthmetrics.model.{DigitalService, MetricFilter, TeamName}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class TeamsAndRepositoriesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with HttpClientV2Support
     with MockitoSugar
     with WireMockSupport:

  private given HeaderCarrier = HeaderCarrier()

  private val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.teams-and-repositories.port" -> wireMockPort
    , "microservice.services.teams-and-repositories.host" -> wireMockHost
    )
  )

  private val connector = TeamsAndRepositoriesConnector(httpClientV2, servicesConfig)

  "TeamsAndRepositoriesConnector.allTeams" should:
    "return all team names" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/v2/teams"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "name": "Team 1",
                    "lastActiveDate": "2025-01-14T13:45:43Z",
                    "repos": [
                      "repo-1",
                      "repo-2",
                      "repo-3"
                    ]
                  },
                  {
                    "name": "Team 2",
                    "lastActiveDate": "2025-06-11T08:31:48Z",
                    "repos": [
                      "repo-2",
                      "repo-3"
                    ]
                  },
                  {
                    "name": "Team 3",
                    "lastActiveDate": "2021-06-17T18:05:19Z",
                    "repos": [
                      "repo-4"
                    ]
                  }
                ]"""

      connector.allTeams().futureValue shouldBe Seq(TeamName("Team 1"), TeamName("Team 2"), TeamName("Team 3"))

  "TeamsAndRepositoriesConnector.allDigitalServices" should:
    "return all digital services" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/v2/digital-services"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  "Digital Service 1",
                  "Digital Service 2"
                ]"""

      connector.allDigitalServices().futureValue shouldBe Seq(DigitalService("Digital Service 1"), DigitalService("Digital Service 2"))

  "TeamsAndRepositoriesConnector.openPullRequestsForReposOwnedByTeam" should:
    "return the number of open pull requests owned by a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/open-pull-requests?reposOwnedByTeam=Team+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1",
                    "title": "fixes stuff",
                    "url": "https://github.com/hmrc/repo-1/pull/546",
                    "author": "joebloggs",
                    "createdAt": "2025-06-11T16:34:41Z"
                  },
                  {
                    "repoName": "repo-2",
                    "title": "fixes more stuff",
                    "url": "https://github.com/hmrc/repo-2/pull/213",
                    "author": "dependabot",
                    "createdAt": "2024-10-17T16:32:04Z"
                  },
                  {
                    "repoName": "repo-3",
                    "title": "adds stuff",
                    "url": "https://github.com/hmrc/repo-3/pull/30",
                    "author": "dependabot",
                    "createdAt": "2024-10-03T17:08:14Z"
                  }
                ]"""

      connector.openPullRequestsForReposOwnedByTeam(TeamName("Team 1")).futureValue shouldBe 3

  "TeamsAndRepositoriesConnector.openPullRequestsForReposOwnedByDigitalService" should:
    "return the number of open pull requests owned by a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/open-pull-requests?reposOwnedByDigitalService=Digital+Service+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1",
                    "title": "fixes stuff",
                    "url": "https://github.com/hmrc/repo-1/pull/546",
                    "author": "joebloggs",
                    "createdAt": "2025-06-11T16:34:41Z"
                  },
                  {
                    "repoName": "repo-2",
                    "title": "fixes more stuff",
                    "url": "https://github.com/hmrc/repo-2/pull/213",
                    "author": "dependabot",
                    "createdAt": "2024-10-17T16:32:04Z"
                  },
                  {
                    "repoName": "repo-3",
                    "title": "adds stuff",
                    "url": "https://github.com/hmrc/repo-3/pull/30",
                    "author": "dependabot",
                    "createdAt": "2024-10-03T17:08:14Z"
                  }
                ]"""

      connector.openPullRequestsForReposOwnedByDigitalService(DigitalService("Digital Service 1")).futureValue shouldBe 3

  "TeamsAndRepositoriesConnector.openPullRequestsRaisedByMembersOfTeam" should:
    "return the number of open pull requests raised by members of a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/open-pull-requests?raisedByMembersOfTeam=Team+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1",
                    "title": "fixes stuff",
                    "url": "https://github.com/hmrc/repo-1/pull/546",
                    "author": "joebloggs",
                    "createdAt": "2025-06-11T16:34:41Z"
                  },
                  {
                    "repoName": "repo-2",
                    "title": "fixes more stuff",
                    "url": "https://github.com/hmrc/repo-2/pull/213",
                    "author": "dependabot",
                    "createdAt": "2024-10-17T16:32:04Z"
                  },
                  {
                    "repoName": "repo-3",
                    "title": "adds stuff",
                    "url": "https://github.com/hmrc/repo-3/pull/30",
                    "author": "dependabot",
                    "createdAt": "2024-10-03T17:08:14Z"
                  }
                ]"""

      connector.openPullRequestsRaisedByMembersOfTeam(TeamName("Team 1")).futureValue shouldBe 3

  "TeamsAndRepositoriesConnector.findTestJobs" should:
    "return test jobs for a team" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/test-jobs?teamName=Team+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1-tests",
                    "jobName": "repo-1-tests-job",
                    "jenkinsURL": "https://build.tax.service.gov.uk/job/repo-1-tests/job/repo-1-tests-job/",
                    "jobType": "test",
                    "repoType": "Test",
                    "testType": "Acceptance",
                    "latestBuild": {
                      "number": 3,
                      "url": "https://build.tax.service.gov.uk//job/repo-1-tests/job/repo-1-tests-job/3/",
                      "timestamp": "2025-05-07T13:10:31.537Z",
                      "result": "FAILURE",
                      "description": "Accessibility issues: N/A, Security alerts: N/A",
                      "testJobResults": {
                        "testJobBuilder": "UITestJobBuilder",
                        "rawJson": {
                          "testType": "UITests",
                          "testJobBuilder": "UITestJobBuilder",
                          "testDuration": "74"
                        }
                      }
                    }
                  },
                  {
                    "repoName": "repo-1-acceptance-tests",
                    "jobName": "repo-1-ui-tests",
                    "jenkinsURL": "https://build.tax.service.gov.uk/job/Agents/job/repo-1-ui-tests/",
                    "jobType": "test",
                    "repoType": "Test",
                    "testType": "Acceptance",
                    "latestBuild": {
                      "number": 513,
                      "url": "https://build.tax.service.gov.uk/job/Agents/job/repo-1-ui-tests/513/",
                      "timestamp": "2025-06-12T11:28:29.277Z",
                      "result": "SUCCESS",
                      "description": "Accessibility issues: 0, Security alerts: 2",
                      "testJobResults": {
                        "numAccessibilityViolations": 0,
                        "numSecurityAlerts": 2,
                        "securityAssessmentBreakdown": {
                          "High": 0,
                          "Medium": 2,
                          "Low": 0,
                          "Informational": 2329
                        },
                        "testJobBuilder": "UITestJobBuilder",
                        "rawJson": {
                          "testType": "UITests",
                          "testJobBuilder": "UITestJobBuilder",
                          "testDuration": "629",
                          "securityAlerts": "2",
                          "alertsSummary": {
                            "High": 0,
                            "Low": 0,
                            "Medium": 2,
                            "Informational": 2329
                          },
                          "accessibilityViolations": "0"
                        }
                      }
                    }
                  }
                ]"""

      connector
        .findTestJobs(TeamName("Team 1"): MetricFilter)
        .futureValue shouldBe Seq(
          JenkinsJob(Some(BuildData(Some("FAILURE"), Some(TestJobResults(None   , None   )))))
        , JenkinsJob(Some(BuildData(Some("SUCCESS"), Some(TestJobResults(Some(0), Some(2))))))
        )

    "return test jobs for a digital service" in:
      stubFor:
        WireMock.get(urlEqualTo("/api/test-jobs?digitalService=Digital+Service+1"))
          .willReturn:
            aResponse()
              .withStatus(200)
              .withBody:
                s"""[
                  {
                    "repoName": "repo-1-tests",
                    "jobName": "repo-1-tests-job",
                    "jenkinsURL": "https://build.tax.service.gov.uk/job/repo-1-tests/job/repo-1-tests-job/",
                    "jobType": "test",
                    "repoType": "Test",
                    "testType": "Acceptance",
                    "latestBuild": {
                      "number": 3,
                      "url": "https://build.tax.service.gov.uk//job/repo-1-tests/job/repo-1-tests-job/3/",
                      "timestamp": "2025-05-07T13:10:31.537Z",
                      "result": "FAILURE",
                      "description": "Accessibility issues: N/A, Security alerts: N/A",
                      "testJobResults": {
                        "testJobBuilder": "UITestJobBuilder",
                        "rawJson": {
                          "testType": "UITests",
                          "testJobBuilder": "UITestJobBuilder",
                          "testDuration": "74"
                        }
                      }
                    }
                  },
                  {
                    "repoName": "repo-1-acceptance-tests",
                    "jobName": "repo-1-ui-tests",
                    "jenkinsURL": "https://build.tax.service.gov.uk/job/Agents/job/repo-1-ui-tests/",
                    "jobType": "test",
                    "repoType": "Test",
                    "testType": "Acceptance",
                    "latestBuild": {
                      "number": 513,
                      "url": "https://build.tax.service.gov.uk/job/Agents/job/repo-1-ui-tests/513/",
                      "timestamp": "2025-06-12T11:28:29.277Z",
                      "result": "SUCCESS",
                      "description": "Accessibility issues: 0, Security alerts: 2",
                      "testJobResults": {
                        "numAccessibilityViolations": 0,
                        "numSecurityAlerts": 2,
                        "securityAssessmentBreakdown": {
                          "High": 0,
                          "Medium": 2,
                          "Low": 0,
                          "Informational": 2329
                        },
                        "testJobBuilder": "UITestJobBuilder",
                        "rawJson": {
                          "testType": "UITests",
                          "testJobBuilder": "UITestJobBuilder",
                          "testDuration": "629",
                          "securityAlerts": "2",
                          "alertsSummary": {
                            "High": 0,
                            "Low": 0,
                            "Medium": 2,
                            "Informational": 2329
                          },
                          "accessibilityViolations": "0"
                        }
                      }
                    }
                  }
                ]"""

      connector
        .findTestJobs(DigitalService("Digital Service 1"): MetricFilter)
        .futureValue shouldBe Seq(
          JenkinsJob(Some(BuildData(Some("FAILURE"), Some(TestJobResults(None   , None   )))))
        , JenkinsJob(Some(BuildData(Some("SUCCESS"), Some(TestJobResults(Some(0), Some(2))))))
        )
