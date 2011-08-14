//import org.clapper.sbt_izpack.IzPack._

name := "SBT IzPack Plugin Test"

version := "0.2"

organization := "org.clapper"

scalaVersion := "2.8.1"

seq(org.clapper.sbt_izpack.IzPack.izPackSettings: _*)

configGenerator in IzPack := Some(IzPackConfiguration)
