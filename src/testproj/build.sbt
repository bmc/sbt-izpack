name := "SBT IzPack Plugin Test"

version := "0.4"

organization := "org.clapper"

scalaVersion := "2.11.3"

libraryDependencies += "org.clapper" %% "grizzled-scala" % "1.3"

configFile in IzPack <<= baseDirectory(_ / "src" / "install.yml")

installSourceDir in IzPack <<= baseDirectory(_ / "src" / "installer")

variables in IzPack <<= (libraryDependencies) {l =>
  Seq(("libs", l.map(_.getClass.getName).toString))
}

logLevel in IzPack := Level.Debug
