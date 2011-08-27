// ---------------------------------------------------------------------------
// SBT 0.10.x Build File for SBT IzPack Plugin
//
// Copyright (c) 2010-2011 Brian M. Clapper
//
// See accompanying license file for license information.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Basic settings

name := "sbt-izpack"

version := "0.1.1"

sbtPlugin := true

organization := "org.clapper"

scalaVersion := "2.8.1"

// ---------------------------------------------------------------------------
// Additional compiler options and plugins

scalacOptions ++= Seq("-deprecation", "-unchecked")

crossScalaVersions := Seq("2.8.1", "2.9.0", "2.9.0-1")

// ---------------------------------------------------------------------------
// Posterous-SBT

libraryDependencies <<= (sbtVersion, scalaVersion, libraryDependencies) { (sbtv, scalav, deps) =>
    if (scalav == "2.8.1")
        deps :+ "net.databinder" %% "posterous-sbt" % ("0.3.0_sbt" + sbtv)
    else
        deps
}

(name in Posterous) := "sbt-izpack"

// ---------------------------------------------------------------------------
// Other dependendencies

// External deps
libraryDependencies ++= Seq(
    "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.4" % "compile",
    "org.yaml" % "snakeyaml" % "1.9",
    "org.clapper" %% "grizzled-scala" % "1.0.7"
)

// ---------------------------------------------------------------------------
// Publishing criteria

publishTo <<= version {(v: String) =>
    val nexus = "http://nexus.scala-tools.org/content/repositories/"
    if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "snapshots/") 
    else                             Some("releases"  at nexus + "releases/")
}

publishMavenStyle := true

credentials += Credentials(Path.userHome / "src" / "mystuff" / "scala" /
                           "nexus.scala-tools.org.properties")

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Compile, packageSrc) := false
