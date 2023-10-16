import Dependencies._

ThisBuild / organization := "guara"
ThisBuild / version := "v1.0.0-SNAPSHOT"

topLevelDirectory       := None
executableScriptName    := "run"
Universal / packageName := "package"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(BuildHelper.stdSettings)
  .settings(
    name := "Guara Web Framework",
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `better-files`, `commons-lang3`,
      `jjwt-api`,`jjwt-impl`, `jjwt-jackson`,
      `zio-kafka`,
      `zio-logging`, `zio-logging-slf4j2`, `logback-classic`,
      `zio-http`, `zio-json`,
      `zio-config`, `zio-config-magnolia`, `zio-config-typesafe`,
      `quill-zio`, `quill-jdbc-zio`, `postgresql`
    )
  )
  .settings(
    Docker / version          := version.value,
    doc / sources             := Seq.empty,
    packageDoc / publishArtifact := false,
    Compile / run / mainClass := Option("sample.SampleApp"),
  )
