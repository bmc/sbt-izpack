---
title: The SBT IzPack Plugin
layout: withTOC
---

**NOT DONE YET!**

# Introduction

The [IzPack][] SBT Plugin is a plugin for the [Scala][]-based [SBT][]
0.10.x build tool. IzPack is an open source tool that allows you to create
flexible Java-based graphical and command-line installers. This plugin
allows you to use IzPack directly from your SBT 0.10.x project.

This document explains how to use the plugin.

# Differences from previous versions

This version of the plugin replaces an [SBT 0.7-based version][], and this
new version is a complete rewrite. The biggest difference between the two
versions of the plugin is the configuration syntax. The previous SBT 0.7
plugin used a Scala-based [DSL][] configuration syntax. This new version
uses an external [YAML][] configuration file, instead, and translates the YAML
file to the [IzPack XML][] installation file format.

The reasons for the switch to YAML include:

* The YAML configuration syntax more closely tracks the syntax of the
  official [IzPack XML][] installation files, making it simpler to
  understand how the plugin's configuration maps to the underlying XML
  configuration.
* The YAML configuration syntax is more compact; compared to the previous
  Scala DSL syntax, the YAML format make it easier to see the entire
  configuration at a glance.

However, switching from a previous configuration to this new one requires
rewriting your configuration.

[SBT 0.7-based version]: http://software.clapper.org/sbt-plugins/izpack.html

# Motivation

There are several reasons I wrote this plugin, but two reasons are paramount.

## Ease of invocation

There was no simple way to invoke the IzPack compiler from SBT. Rather than
craft a clumsy way to invoke IzPack via the command line, I wrote this
plugin so I could invoke it directly from within SBT.

## XML is a lousy configuration file format

IzPack uses a complex XML configuration file.

I dislike the whole trend of using XML as a configuration file format. Yes,
XML is ostensibly human-readable. But it isn't always human-*friendly*. I
agree with [Terence Parr][], author of [ANTLR][] and [StringTemplate][],
who wrote that [humans should not have to grok XML][grok-XML].

[Terence Parr]: http://www.cs.usfca.edu/~parrt/
[ANTLR]: http://www.antlr.org/
[StringTemplate]: http://stringtemplate.org/
[grok-xml]: http://www.ibm.com/developerworks/xml/library/x-sbxml.html

YAML is often a simpler, easier to read configuration syntax, and it can
model many of the same constructs as XML. Translating a YAML configuration
into the the IzPack XML syntax turns out to be straightforward.

# Getting the Plugin

First, within your SBT project, create `project/plugins/build.sbt` (if it
doesn't already exist) and add the following:

    // The plugin is only published for 2.8.1
    libraryDependencies <<= (scalaVersion, libraryDependencies) { (scv, deps) =>
        if (scv == "2.8.1")
            deps :+ "org.clapper" %% "sbt-izpack" % "0.1"
        else
            deps
    }

Next, in your main project `build.sbt` file, add:

    seq(org.clapper.sbt.izpack.IzPack.izPackSettings: _*)

Now the plugin is available to your SBT builds.

# Settings

The plugin provides the following new settings.


---

**`installSourceDir`**

---

The directory containing additional installer source files, such as the
HTML files for install screens.

Example:

    installSourceDir in IzPack <<= baseDirectory(_ / "src" / "install")
    
Default: `baseDirectory(_ / "src" / "izpack")`

---

**`configFile`**

---

The YAML file describing the installer configuration. (See the
section entitled [The YAML configuration file](#the_yaml_configuration_file)
for details.) Examples:

    sourceFile in IzPack <<= baseDirectory(_ / "src" / "install" / "install.yml")

Default: `installSourceDir(_ / "izpack.yml")`

---

**`installerJar`**

---

The path to the installer jar file IzPack is to generate.

Example:

    installerJar in IzPack <<= baseDirectory(_ / "target" / "install.jar")

Default: `baseDirectory(_ / "target" / "installer.jar")`

---

**`variables`**

---

`variables` is a sequence of `(variableName, value)` pairs. For instance,
the following two lines define:

* a `${projectName}` variable that substitutes the name of the project, and
* a `${author}` variable

    name := "my-project"

    variables in IzPack <+= name {name => ("projectName", name)}

    variables in IzPack += ("author", "Brian Clapper")

These variables can be substituted within the YAML configuration file and
augment the [predefined variables](#predefined_variables) the plugin defines.


# Tasks

The plugin provides two new SBT tasks.

* `editsource:edit` performs the edits on each source file that is out
  of date with respect to its corresponding target file. If no variable
  substitutions or regular expression substitutions are specified,
  `editsource:edit` does nothing.

* `editsource:clean` deletes all target edited files. `editsource:clean`
  is also automatically linked into the main SBT `clean` task.

# The YAML configuration file

## Variables

Inside a source file to be edited, variable references are of the form
`${varname}`, as in the Unix shell. A shortened `$varname` is also support.
The `${}` long form also supports a default syntax: `${varname?default}`.
If the reference variable has no value, then the default value is supplied,
instead. (The `?default` syntax is not supported for the short form
reference.)

With the above definitions in place, when the source files are edited, any
reference to `${projectName}` is replaced with "my-project", and any
reference to `${author}` is replaced with "Brian Clapper".

You can define any number of variables. If the edit logic encounters a
variable that isn't defined, it simply replaces the variable reference with
an empty string (like *bash* does).

In addition to the variables you define in your build file, the
*sbt-editsource* also honors the following special variable prefixes:

* `env.`: Any variable starting with `env.` is assumed to be an environment
  variable reference. For instance, `${env.HOME}` will substitute the value
  of the "HOME" environment variable.
* `sys.now`: The current date and time, in "yyyy/mm/dd HH:MM:ss" form. For
  example: `${sys.now}` might yield "2011/08/17 13:01:56"
* `sys.today`: The current date, in "yyyy/mm/dd" form.
* `sys.*something*`: Any other variable name starting with `sys.` is
  assumed to refer to a JVM system property and is resolved via a call to
  `java.lang.System.getProperty()`. Thus, `${sys.user.name}` substitutes
  the `user.name` property, and `${sys.java.io.tmpdir}` substitutes the
  `java.io.tmpdir` property.

### Predefined variables

# Restrictions

* Currently, *sbt-editsource* only supports one set of edits, applied to
  *all* specified files. That is, you cannot specify one set of edits for
  one group of files and a second set of edits for a different group of
  files. In the future, the plugin may be enhanced to support this
  capability.

# Change log

The change log for all releases is [here][changelog].

# Author

Brian M. Clapper, [bmc@clapper.org][]

# Copyright and License

This software is copyright &copy; 2010-2011 Brian M. Clapper and is
released under a [BSD License][].

# Patches

I gladly accept patches from their original authors. Feel free to email
patches to me or to fork the [GitHub repository][] and send me a pull
request. Along with any patch you send:

* Please state that the patch is your original work.
* Please indicate that you license the work to the Grizzled-Scala project
  under a [BSD License][].

[BSD License]: license.html
[Scala]: http://www.scala-lang.org/
[GitHub repository]: http://github.com/bmc/sbt-izpack
[GitHub]: https://github.com/bmc/
[Scala Tools Maven repository]: http://www.scala-tools.org/repo-releases/
[Scala Maven Guide]: http://www.scala-lang.org/node/345
[SBT]: https://github.com/harrah/xsbt/
[bmc@clapper.org]: mailto:bmc@clapper.org
[changelog]: CHANGELOG.html
[SBT cross-building]: http://code.google.com/p/simple-build-tool/wiki/CrossBuild
[IzPack XML]: http://izpack.org/documentation/installation-files.html
[Izpack]: http://izpack.org/
[DSL]: http://en.wikipedia.org/wiki/Domain-specific_language
[YAML]: http://yaml.org/
