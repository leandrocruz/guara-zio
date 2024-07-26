import sbt._

object Dependencies {
  val ZioVersion        = "2.1.6"
  val ZioConfigVersion  = "4.0.2"
  val ZHTTPVersion      = "3.0.0-RC9"
  val ZioJsonVersion    = "0.6.2"
  val ZioLoggingVersion = "2.3.0"
  val QuillVersion      = "4.8.5"
  val Logback2Version   = "1.5.6"
  val Slf4j2Version     = "2.0.12"

  val `better-files`          = "com.github.pathikrit"   %% "better-files-akka"          % "3.9.2"
  val `commons-lang3`         = "org.apache.commons"     %  "commons-lang3"              % "3.15.0"
  val `zio-http`              = "dev.zio"                %% "zio-http"                   % ZHTTPVersion changing()
  val `zio-json`              = "dev.zio"                %% "zio-json"                   % ZioJsonVersion
  val `zio-kafka`             = "dev.zio"                %% "zio-kafka"                  % "2.8.0"
  val `zio-config`            = "dev.zio"                %% "zio-config"                 % ZioConfigVersion
  val `zio-config-typesafe`   = "dev.zio"                %% "zio-config-typesafe"        % ZioConfigVersion
  val `zio-config-magnolia`   = "dev.zio"                %% "zio-config-magnolia"        % ZioConfigVersion
  val `zio-logging`           = "dev.zio"                %% "zio-logging"                % ZioLoggingVersion
  val `zio-logging-slf4j2`    = "dev.zio"                %% "zio-logging-slf4j2"         % ZioLoggingVersion
  val `slf4j-api`             = "org.slf4j"              %  "slf4j-api"                  % Slf4j2Version
  val `logback-classic`       = "ch.qos.logback"         %  "logback-classic"            % Logback2Version
  val `quill-zio`             = "io.getquill"            %% "quill-zio"                  % QuillVersion
  val `quill-jdbc-zio`        = "io.getquill"            %% "quill-jdbc-zio"             % QuillVersion
  val `postgresql`            = "org.postgresql"         %  "postgresql"                 % "42.7.3"
  val `zio-test-sbt`          = "dev.zio"                %% "zio-test-sbt"               % ZioVersion   % Test
}
