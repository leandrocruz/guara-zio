lazy val ZioVersion        = "2.1.24"
lazy val ZioConfigVersion  = "4.0.7"
lazy val ZHTTPVersion      = "3.10.1"
lazy val ZioJsonVersion    = "0.9.0"
lazy val ZioLoggingVersion = "2.5.3"
lazy val Logback2Version   = "1.5.18"
lazy val Slf4j2Version     = "2.0.12"
lazy val CirceVersion      = "0.14.12"

ThisBuild / organization := "guara"
ThisBuild / version      := "v1.4.0-SNAPSHOT"

lazy val guara = (project in file("."))
  .aggregate(shared, codecZio, codecCirce, client, clientCodecZio, clientCodecCirce, framework)
  .settings(
    name           := "guara",
    publish / skip := true,
  )

// --- Commons ---

lazy val shared = (project in file("modules/commons/shared"))
  .settings(BuildHelper.stdSettings)
  .settings(
    name                         := "guara-commons-shared",
    doc / sources                := Seq.empty,
    packageDoc / publishArtifact := false,
    libraryDependencies          ++= Seq(
      "dev.zio" %% "zio" % ZioVersion,
    ),
  )

lazy val codecZio = (project in file("modules/commons/codec/zio"))
  .dependsOn(shared)
  .settings(BuildHelper.stdSettings)
  .settings(
    name                         := "guara-commons-codec-zio",
    doc / sources                := Seq.empty,
    packageDoc / publishArtifact := false,
    libraryDependencies          ++= Seq(
      "dev.zio" %% "zio-json" % ZioJsonVersion,
    ),
  )

lazy val codecCirce = (project in file("modules/commons/codec/circe"))
  .dependsOn(shared)
  .settings(BuildHelper.stdSettings)
  .settings(
    name                         := "guara-commons-codec-circe",
    doc / sources                := Seq.empty,
    packageDoc / publishArtifact := false,
    libraryDependencies          ++= Seq(
      "io.circe" %% "circe-core"    % CirceVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
    ),
  )

// --- Client ---

lazy val client = (project in file("modules/client"))
  .settings(BuildHelper.stdSettings)
  .settings(
    name                         := "guara-client",
    doc / sources                := Seq.empty,
    packageDoc / publishArtifact := false,
    libraryDependencies          ++= Seq(
      "dev.zio" %% "zio"      % ZioVersion,
      "dev.zio" %% "zio-http" % ZHTTPVersion,
    ),
  )

lazy val clientCodecZio = (project in file("modules/client/codec/zio"))
  .dependsOn(client, codecZio)
  .settings(BuildHelper.stdSettings)
  .settings(
    name                         := "guara-client-codec-zio",
    doc / sources                := Seq.empty,
    packageDoc / publishArtifact := false,
    libraryDependencies          ++= Seq(
      "dev.zio" %% "zio-json" % ZioJsonVersion,
    ),
  )

lazy val clientCodecCirce = (project in file("modules/client/codec/circe"))
  .dependsOn(client, codecCirce)
  .settings(BuildHelper.stdSettings)
  .settings(
    name                         := "guara-client-codec-circe",
    doc / sources                := Seq.empty,
    packageDoc / publishArtifact := false,
    libraryDependencies          ++= Seq(
      "io.circe" %% "circe-core"   % CirceVersion,
      "io.circe" %% "circe-parser" % CirceVersion,
    ),
  )

// --- Framework ---

lazy val framework = (project in file("modules/framework"))
  .dependsOn(codecZio, clientCodecZio)
  .settings(BuildHelper.stdSettings)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name                         := "guara-framework",
    doc / sources                := Seq.empty,
    packageDoc / publishArtifact := false,
    testFrameworks               += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies          ++= Seq(
      "com.github.pathikrit" %% "better-files-akka"   % "3.9.2",
      "org.apache.commons"   %  "commons-lang3"       % "3.20.0",
      "dev.zio"              %% "zio-http"             % ZHTTPVersion,
      "dev.zio"              %% "zio-json"             % ZioJsonVersion,
      "dev.zio"              %% "zio-kafka"            % "2.8.2",
      "dev.zio"              %% "zio-config"           % ZioConfigVersion,
      "dev.zio"              %% "zio-config-typesafe"  % ZioConfigVersion,
      "dev.zio"              %% "zio-config-magnolia"  % ZioConfigVersion,
      "dev.zio"              %% "zio-logging"          % ZioLoggingVersion,
      "dev.zio"              %% "zio-logging-slf4j2"   % ZioLoggingVersion,
      "ch.qos.logback"       %  "logback-classic"      % Logback2Version,
      "dev.zio"              %% "zio-test-sbt"         % ZioVersion % Test,
    ),
  )
