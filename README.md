IzPack SBT Plugin
=================

# Introduction

This project contains an [IzPack][izpack] plugin for [SBT][sbt] 0.10.1 or
greater.

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

See original [IzPack documentation](http://izpack.org/documentation/installation-files.html#fileset-add-a-fileset).

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
