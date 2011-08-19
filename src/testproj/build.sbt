//import org.clapper.sbt_izpack.IzPack._

name := "SBT IzPack Plugin Test"

version := "0.2"

organization := "org.clapper"

scalaVersion := "2.8.1"

libraryDependencies += "org.clapper" %% "grizzled-scala" % "1.0.7"

seq(org.clapper.sbt.izpack.IzPack.izPackSettings: _*)

configFile in IzPack <<= baseDirectory(_ / "src" / "install.yml")

installSource in IzPack <<= baseDirectory(_ / "src" / "installer")
