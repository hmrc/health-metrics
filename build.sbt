import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion  := 0
ThisBuild / scalaVersion  := "3.3.4"
ThisBuild / scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"

lazy val microservice = Project("health-metrics", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions += "-Wconf:src=routes/.*:s",
  )
  .settings(PlayKeys.playDefaultPort := 8862)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .settings(RoutesKeys.routesImport  ++= Seq(
    "uk.gov.hmrc.healthmetrics.util.Binders.given"
  , "uk.gov.hmrc.healthmetrics.model.DigitalService"
  , "uk.gov.hmrc.healthmetrics.model.TeamName"
  ))

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)
