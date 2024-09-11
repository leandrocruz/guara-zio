import sbt._
import sbt.Keys._

object BuildHelper {
  val Scala3   = "3.3.3"
  val Scala212 = "2.12.19"
  val Scala213 = "2.13.14"

  def commonSettings(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
    case Some((3, _))                  => Seq.empty
    case Some((2, 12)) | Some((2, 13)) => Seq("-Ywarn-unused:params")
    case _                             => Seq.empty
  }
  def stdSettings = Seq(
    ThisBuild / fork               := true,
    //ThisBuild / crossScalaVersions := List(Scala212, Scala213, Scala3),
    ThisBuild / scalaVersion       := Scala3,
    ThisBuild / scalacOptions      := commonSettings(scalaVersion.value),
  )
}
