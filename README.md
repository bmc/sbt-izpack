IzPack SBT Plugin
=================

# Introduction

This project contains an [IzPack][izpack] plugin for [SBT][sbt] 0.10.1 or
greater.

For SBT 0.7.x, see the [previous version of this plugin][].

[sbt]: https://github.com/harrah/xsbt/
[izpack]: http://izpack.org/

[previous version of this plugin]: http://software.clapper.org/sbt-plugins/izpack.html

# WARNING

**This plugin is still being ported from SBT 0.7.x! It has not yet been
fully tested, and it may not yet be in its final form!**

**NOT SUPPORTED: `conditions`, `dynamicvariables`, `variables` **

# Using the Plugin

First, within your SBT project, create `project/plugins/build.sbt` (if it
doesn't already exist) and add the following:

    // The IzPack plugin is only published for 2.8.1
    libraryDependencies <<= (scalaVersion, libraryDependencies) { (scv, deps) =>
        if (scv == "2.8.1")
            deps :+ "org.clapper" %% "sbt-izpack" % "0.4"
        else
            deps
    }

Next, in your main project `build.sbt` file, add:

    seq(org.clapper.sbt_izpack.IzPack.izPackSettings: _*)

Now the plug-in is available.

## Tasks and Settings

The plugin provides these new SBT tasks:

* `izpack:create-xml`: Create the IzPack XML configuration file from the
  Scala-based one.
* `izpack:create-installer`: Create the IzPack XML configuration and use it
  to build the installer jar file.

It also uses these SBT settings:

* `configGenerator in IzPack`: An `Option[IzPackConfigurator]` that defines
  the Scala-based IzPack configuration. Defaults to `None`.
* `installerJar in IzPack`: The path to the installer jar file to generate.
  Defaults to: *project_root*`/target/installer.jar`
* `installDir in IzPack`: The path to a directory containing additional
  installer files (e.g., the license file, files to support various IzPack
  panels, shell scripts, etc.). Defaults to: *project_root*`/src/installer`

## The Scala-based Configuration

To create your IzPack configuration, create a Scala source file in your
`project` directory (e.g., `IzPack.scala`). In that file, create an object
that extends the `IzPackConfigurator` trait and provides a `makeConfig`
method to generate the configuration. That method must return an object of
type `IzPackConfig`. For instance:

    import org.clapper.sbt.IzPack._
    import sbt._

    object IzPackConfiguration extends IzPackConfigurator
    {
        def makeConfig(installSourceDir: RichFile, scalaVersion: String) =
        {
            new IzPackConfig
            {
                ...
            }
        }
    }

Now, back in your main `build.sbt` file, wire that object in:

    configGenerator in IzPack := Some(IzPackConfiguration)

Please see the [IzPack Plugin web site][] for detailed usage instructions.

[IzPack Plugin web site]: http://software.clapper.org/sbt-izpack/

# License

This plugin is released under a BSD license, adapted from
<http://opensource.org/licenses/bsd-license.php>

Copyright &copy; 2010-2011, Brian M. Clapper
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

* Redistributions of source code must retain the above copyright notice,
  this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.

* Neither the names "clapper.org", "sbt-izpack", nor the names of any
  contributor may be used to endorse or promote products derived from this
  software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
