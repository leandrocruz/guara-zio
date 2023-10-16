import sbt._

object Dependencies {
  val ZioVersion        = "2.0.17"
  val ZioConfigVersion  = "4.0.0-RC16"
  val ZHTTPVersion      = "3.0.0-RC2+118-32a59d9c-SNAPSHOT" //0.0.0+1435-c6c11f9f-SNAPSHOT" //3.0.0-RC2+78-8780eb95+20230919-1705-SNAPSHOT" //"3.0.0-RC2+78-8780eb95-SNAPSHOT"
  val ZioJsonVersion    = "0.6.2"
  val JJwtVersion       = "0.11.5"
  val ZioLoggingVersion = "2.1.14"
  val QuillVersion      = "4.6.0"
  val Logback2Version   = "1.4.11"
  val Slf4j2Version     = "2.0.9"

  val `better-files`          = "com.github.pathikrit"   %% "better-files-akka"          % "3.9.2"
  val `commons-lang3`         = "org.apache.commons"     %  "commons-lang3"              % "3.10"
  val `jjwt-api`              = "io.jsonwebtoken"        % "jjwt-api"                    % JJwtVersion
  val `jjwt-impl`             = "io.jsonwebtoken"        % "jjwt-impl"                   % JJwtVersion
  val `jjwt-jackson`          = "io.jsonwebtoken"        % "jjwt-jackson"                % JJwtVersion
  val `zio-http`              = "dev.zio"                %% "zio-http"                   % ZHTTPVersion changing()
  val `zio-json`              = "dev.zio"                %% "zio-json"                   % ZioJsonVersion
  val `zio-kafka`             = "dev.zio"                %% "zio-kafka"                  % "2.4.2"
  val `zio-config`            = "dev.zio"                %% "zio-config"                 % ZioConfigVersion
  val `zio-config-typesafe`   = "dev.zio"                %% "zio-config-typesafe"        % ZioConfigVersion
  val `zio-config-magnolia`   = "dev.zio"                %% "zio-config-magnolia"        % ZioConfigVersion
  val `zio-logging`           = "dev.zio"                %% "zio-logging"                % ZioLoggingVersion
  val `zio-logging-slf4j2`    = "dev.zio"                %% "zio-logging-slf4j2"         % ZioLoggingVersion
  val `slf4j-api`             = "org.slf4j"              %  "slf4j-api"                  % Slf4j2Version
  val `logback-classic`       = "ch.qos.logback"         %  "logback-classic"            % Logback2Version
  val `quill-zio`             = "io.getquill"            %% "quill-zio"                  % QuillVersion
  val `quill-jdbc-zio`        = "io.getquill"            %% "quill-jdbc-zio"             % QuillVersion
  val `postgresql`            = "org.postgresql"         %  "postgresql"                 % "42.2.8"
  val `zio-test-sbt`          = "dev.zio"                %% "zio-test-sbt"               % ZioVersion   % Test
}
