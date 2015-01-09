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

version := "1.0.0"

sbtPlugin := true

organization := "org.clapper"

licenses := Seq("BSD-like" ->
  url("http://software.clapper.org/sbt-izpack/license.html")
)

description := "SBT plugin to generate an IzPack installer"

// ---------------------------------------------------------------------------
// Additional compiler options and plugins

scalacOptions ++= Seq("-deprecation", "-unchecked")

crossScalaVersions := Seq("2.10.4")

seq(lsSettings :_*)

(LsKeys.tags in LsKeys.lsync) := Seq("izpack", "installer")

(description in LsKeys.lsync) <<= description(d => d)

// ---------------------------------------------------------------------------
// Other dependendencies

// External deps
libraryDependencies ++= Seq(
  "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.5" % "compile",
  "org.yaml" % "snakeyaml" % "1.14"
)

libraryDependencies += "org.clapper" %% "grizzled-scala" % "1.3"

// ---------------------------------------------------------------------------
// Publishing criteria

publishMavenStyle := false

publishArtifact in (Compile, packageBin) := true

publishArtifact in (Test, packageBin) := false

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Compile, packageSrc) := false
