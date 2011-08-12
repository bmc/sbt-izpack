name := "SBT IzPack Plugin Test"

version := "0.2"

organization := "org.clapper"

scalaVersion := "2.8.1"

libraryDependencies += "org.clapper" %% "sbt-izpack" % "0.4"

(configuration in IzPack) := Some(new IzPackConfig("target" / "install")
{
})
