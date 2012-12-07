name := "SBT IzPack Plugin Test"

version := "0.3"

organization := "org.clapper"

libraryDependencies += "org.clapper" %% "grizzled-scala" % "1.0.13"

seq(IzPack.settings: _*)

IzPack.configFile in IzPack.Config <<= baseDirectory(_ / "src" / "install.yml")

IzPack.installSourceDir in IzPack.Config <<= baseDirectory(_ / "src" / "installer")

IzPack.variables in IzPack.Config <<= (libraryDependencies) {l =>
  Seq(("libs", l.map(_.getClass.getName).toString))
}

IzPack.logLevel in IzPack.Config := Level.Debug
