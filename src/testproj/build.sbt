name := "SBT IzPack Plugin Test"

version := "0.2"

organization := "org.clapper"

scalaVersion := "2.8.1"

libraryDependencies += "org.clapper" %% "grizzled-scala" % "1.0.7"

libraryDependencies += "net.databinder" %% "posterous-sbt" % "0.3.0_sbt0.10.1"

seq(IzPack.settings: _*)

IzPack.configFile <<= baseDirectory(_ / "src" / "install.yml")

IzPack.installSourceDir <<= baseDirectory(_ / "src" / "installer")

IzPack.variables <<= (libraryDependencies) {l =>
  Seq(("libs", l.map(_.getClass.getName).toString))
}

IzPack.logLevel := Level.Debug
