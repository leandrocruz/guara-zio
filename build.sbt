lazy val ZioVersion        = "2.1.16"
lazy val ZioConfigVersion  = "4.0.3"
lazy val ZHTTPVersion      = "3.1.0"
lazy val ZioJsonVersion    = "0.7.39"
lazy val ZioLoggingVersion = "2.5.0"
lazy val Logback2Version   = "1.5.17"
lazy val Slf4j2Version     = "2.0.12"

organization                 := "guara"
name                         := "guara-framework"
version                      := "v1.1.6"
doc / sources                := Seq.empty
packageDoc / publishArtifact := false
testFrameworks               += new TestFramework("zio.test.sbt.ZTestFramework")
libraryDependencies          ++= Seq(
  "com.github.pathikrit" %% "better-files-akka"          % "3.9.2"                 ,
  "org.apache.commons"   %  "commons-lang3"              % "3.17.0"                ,
  "dev.zio"              %% "zio-http"                   % ZHTTPVersion            ,
  "dev.zio"              %% "zio-json"                   % ZioJsonVersion          ,
  "dev.zio"              %% "zio-kafka"                  % "2.8.2"                 ,
  "dev.zio"              %% "zio-config"                 % ZioConfigVersion        ,
  "dev.zio"              %% "zio-config-typesafe"        % ZioConfigVersion        ,
  "dev.zio"              %% "zio-config-magnolia"        % ZioConfigVersion        ,
  "dev.zio"              %% "zio-logging"                % ZioLoggingVersion       ,
  "dev.zio"              %% "zio-logging-slf4j2"         % ZioLoggingVersion       ,
  "ch.qos.logback"       %  "logback-classic"            % Logback2Version         ,
  "dev.zio"              %% "zio-test-sbt"               % ZioVersion   % Test     ,
)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(BuildHelper.stdSettings)