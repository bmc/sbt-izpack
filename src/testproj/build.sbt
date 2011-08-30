name := "SBT IzPack Plugin Test"

version := "0.2"

organization := "org.clapper"

scalaVersion := "2.8.1"

libraryDependencies += "org.clapper" %% "grizzled-scala" % "1.0.7"

libraryDependencies += "net.databinder" %% "posterous-sbt" % "0.3.0_sbt0.10.1"

seq(org.clapper.sbt.izpack.IzPack.izPackSettings: _*)

IzPack.configFile in IzPack.Config <<= baseDirectory(_ / "src" / "install.yml")

IzPack.installSourceDir in IzPack.Config <<= 
  baseDirectory(_ / "src" / "installer")

IzPack.variables in IzPack.Config <<= (libraryDependencies) {l =>
  Seq(("libs", l.map(_.getClass.getName).toString))
}

IzPack.logLevel in IzPack.Config := Level.Debug
