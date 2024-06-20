import Dependencies._

organization                 := "guara"
name                         := "guara-framework"
version                      := "v0.0.4-SNAPSHOT"
doc / sources                := Seq.empty
packageDoc / publishArtifact := false
testFrameworks               += new TestFramework("zio.test.sbt.ZTestFramework")
libraryDependencies          ++= Seq(
  `better-files`, `commons-lang3`, `zio-kafka`, `zio-logging`, `zio-logging-slf4j2`,
  `logback-classic`, `zio-http`, `zio-json`, `zio-config`, `zio-config-magnolia`, `zio-config-typesafe`, `quill-zio`,
  `quill-jdbc-zio`, `postgresql`
)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(BuildHelper.stdSettings)