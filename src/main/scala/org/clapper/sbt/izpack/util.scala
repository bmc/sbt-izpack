/*
  ---------------------------------------------------------------------------
  This software is released under a BSD license, adapted from
  http://opensource.org/licenses/bsd-license.php

  Copyright (c) 2010-2011, Brian M. Clapper
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
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

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
  ---------------------------------------------------------------------------
*/

package org.clapper.sbt.izpack

import sbt.RichFile
import java.io.File

trait Util
{
    /**
     * Writes a string to a file, overwriting the file.
     *
     * @param path  the SBT path for the file to be written
     * @param str   the string to write
     */
    protected def writeStringToFile(path: RichFile, str: String): Unit =
        writeStringToFile(path.absolutePath, str)

    /**
     * Writes a string to a file, overwriting the file.
     *
     * @param path  the path of the file to be written
     * @param str   the string to write
     */
    protected def writeStringToFile(path: String, str: String): Unit =
    {
        import java.io.FileWriter
        val writer = new FileWriter(path)
        writer.write(str + "\n")
        writer.flush()
        writer.close()
    }

    protected def temporaryFile(ext: String): RichFile =
        temporaryFile("izpack", "ext")

    protected def temporaryFile(prefix: String, ext: String): RichFile =
    {
        val f = File.createTempFile(prefix, ext)
        f.deleteOnExit()
        new RichFile(f)
    }

    protected def izError(msg: String) =
        throw new IzPluginException(msg)

    /**
     * Convert a boolean value to a "yes" or "no" string
     */
    protected def yesno(b: Boolean): String = if (b) "yes" else "no"
}
