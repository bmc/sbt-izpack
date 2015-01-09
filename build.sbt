// ---------------------------------------------------------------------------
// SBT 0.10.x Build File for SBT IzPack Plugin
//
// Copyright (c) 2010-2011 Brian M. Clapper
//
// See accompanying license file for license information.
// ---------------------------------------------------------------------------

import bintray.Keys._

// ---------------------------------------------------------------------------
// Basic settings

name := "sbt-izpack"

version := "1.0.0"

sbtPlugin := true

organization := "org.clapper"

licenses += ("BSD New", url("https://github.com/bmc/sbt-izpack/blob/master/LICENSE.md"))

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

bintrayPublishSettings

repository in bintray := "sbt-plugins"

bintrayOrganization in bintray := None

