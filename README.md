IzPack SBT Plugin
=================

# Introduction

This project contains an [IzPack][izpack] plugin for [SBT][sbt] 0.10.1 or
greater.

# Fork

This is a fork of the original author [located here](https://github.com/bmc/sbt-izpack/).  
It has only one difference.  It implements a new element called **fset**.  Instead of the
original **fileset** element who does have logic for finding files, it delegates all the
hard work to **IzPack**.

The syntax is:

    fset:
        # The directory where to search source files.  All paths in "includes" and "excludes"
        # are relative to this.
        dir: /mypath
        # Directory where to install the files
        targetdir: $INSTALLPATH/destinationFolder
        # Include several patterns that may use ** and *, separated by spaces or commas.  If omitted, include all files
        includes: *.jar, **/*/*.class
        # Same syntax as "includes".  Excludes are processed after includes.
        excludes: .svn/**, **/Test*

See original [IzPack documentation](http://izpack.org/documentation/installation-files.html#fileset-add-a-fileset), 
for further details about IzPack's `fileset`.

[This issue](https://github.com/bmc/sbt-izpack/issues/13) explains the motivation of this fork.

# Use

In order to use this plugin, add this line to `build.sbt`:

    addSbtPlugin("com.github.dapeca" % "sbt-izpack" % "1.0.3")

# variablesExportPrefixes

The original version of this plugin exports to `IzPack` many variables when building the `izpack.xml` file.

It may seem inofensive, but `IzPack` replaces in file names any $ followed by a matched variable name.

Let's look at an example.  Suppose, you have a variable called `Memory`, and a class called `MemoryLimit` that is nested to class `Settings`.
In that case, `Settings$MemoryLimit.class` would be generated after compilation and `$Memory` replaced by IzPack in the file name.

Languages like Scala use a lot of nested classes.

If the value of the `MemoryLimit` variable is `20`, the the file would be renamed to `Settings$20.class`, and a `ClassNotFound` exception would be thrown at runtime.

For avoiding this, I use a prefix to all the variables name that must be processed by IzPack and `parsable` entries, i.e. the prefix `izPack`.
 
In that case, the setting would be declared in `build.sbt` as:

    variablesExportPrefixes in IzPack := Seq("izPack")


# Change log

- Version 1.0.3
    - Publishing with a shorter organizacion: com.github.DavidPerezIngeniero ⇒ com.github.dapeca
- Version 1.0.2
    - New setting called `variablesExportPrefixes`.
- Version 1.0.1
    - Solved [Actions of a panel don't work](https://github.com/bmc/sbt-izpack/issues/18).
- Version 1.0.0
    - [New fset element](https://github.com/bmc/sbt-izpack/issues/13).

# Original documentation

For SBT 0.7.x, see the [previous version of this plugin][].

For complete documentation, see the [IzPack Plugin web site][].

[sbt]: https://github.com/harrah/xsbt/
[izpack]: http://izpack.org/
[previous version of this plugin]: http://software.clapper.org/sbt-plugins/izpack.html
[IzPack Plugin web site]: http://software.clapper.org/sbt-izpack/

# License

This plugin is released under a BSD license, adapted from
<http://opensource.org/licenses/bsd-license.php>

Copyright &copy; 2010-2012, Brian M. Clapper
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
