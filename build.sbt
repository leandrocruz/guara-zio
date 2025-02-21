lazy val ZioVersion        = "2.1.6"
lazy val ZioConfigVersion  = "4.0.2"
lazy val ZHTTPVersion      = "3.0.1"
lazy val ZioJsonVersion    = "0.6.2"
lazy val ZioLoggingVersion = "2.3.0"
lazy val QuillVersion      = "4.8.5"
lazy val Logback2Version   = "1.5.6"
lazy val Slf4j2Version     = "2.0.12"

organization                 := "guara"
name                         := "guara-framework"
version                      := "v0.1.1-SNAPSHOT"
doc / sources                := Seq.empty
packageDoc / publishArtifact := false
testFrameworks               += new TestFramework("zio.test.sbt.ZTestFramework")
libraryDependencies          ++= Seq(
  "com.github.pathikrit" %% "better-files-akka"          % "3.9.2"                 ,
  "org.apache.commons"   %  "commons-lang3"              % "3.15.0"                ,
  "dev.zio"              %% "zio-http"                   % ZHTTPVersion changing() ,
  "dev.zio"              %% "zio-json"                   % ZioJsonVersion          ,
  "dev.zio"              %% "zio-kafka"                  % "2.8.2"                 ,
  "dev.zio"              %% "zio-config"                 % ZioConfigVersion        ,
  "dev.zio"              %% "zio-config-typesafe"        % ZioConfigVersion        ,
  "dev.zio"              %% "zio-config-magnolia"        % ZioConfigVersion        ,
  "dev.zio"              %% "zio-logging"                % ZioLoggingVersion       ,
  "dev.zio"              %% "zio-logging-slf4j2"         % ZioLoggingVersion       ,
  //"org.slf4j"            %  "slf4j-api"                  % Slf4j2Version           ,
  "ch.qos.logback"       %  "logback-classic"            % Logback2Version         ,
//  "io.getquill"          %% "quill-zio"                  % QuillVersion            ,
//  "io.getquill"          %% "quill-jdbc-zio"             % QuillVersion            ,
//  "org.postgresql"       %  "postgresql"                 % "42.7.3"                ,
  "dev.zio"              %% "zio-test-sbt"               % ZioVersion   % Test     ,
)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(BuildHelper.stdSettings)