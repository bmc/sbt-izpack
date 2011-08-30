---
title: "Change Log: sbt-izpack"
layout: default
---

Version 0.1.4:

* Put plugin settings into a sub-object, so they don't clash with
  other settings on (auto) import into `build.sbt`. Accessing a setting
  is now accomplished with:

        IzPack.configFile in IzPack.Config <<= baseDirectory(...)

* Converted code to conform with standard Scala coding style.

Version 0.1.3:

* Renamed various plugin settings and variables, so their names wouldn't
  clash, on import, with other plugins.

Version 0.1.2:

* Default log level in *sbt-izpack* is now `Info`, not `Warn`.
* `(createXML in IzPack)` now depends on `(packageBin in Compile)`, so
  compilation will be triggered automatically, before the installer
  configuration is translated.
* `(createInstaller in IzPack)` now depends on `(createXML in IzPack)`.

Version 0.1.1:

* Fixed a problem with `logLevel` handling.
* `failureType` in an `executable` (within a `pack`) now defaults to
  `warn`. (It didn't have a default, but was required.)
* `stage` in an `executable` (within a `pack`) now defaults to
  `never`. (It didn't have a default, but was required.)
* Added missing `type` field to `executable` XML element.
* Now published for Scala 2.8.1, 2.9.0 and 2.9.0-1.
* Predefined variables now contain `$appName` (from SBT's `name` setting),
  `$normalizedAppName` (from SBT's `normalizedName` setting) `$appVersion`
  (from SBT's `version` setting), `$scalaVersion` and `$appJar` (the
  project's generated jar file) and `$classDirectory` (the directory
  containing the compiled classes and the jar file, from SBT's
  `classDirectory` setting).

Version 0.1:

Completely rewrote my SBT 0.7 [IzPack][] plugin for [SBT][] 0.10.x. Now
uses a YAML configuration file, rather than a [Scala][] DSL, for
configuration, resulting in a more compact (and, arguably, more readable)
configuration.

[Izpack]: http://izpack.org/
[Scala]: http://www.scala-lang.org/
[SBT]: https://github.com/harrah/xsbt/


