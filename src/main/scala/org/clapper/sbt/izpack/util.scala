/*
  ---------------------------------------------------------------------------
  This software is released under a BSD license, adapted from
  http://opensource.org/licenses/bsd-license.php

  Copyright (c) 2010-2015, Brian M. Clapper
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

import scala.annotation.tailrec
import scala.xml.{Attribute => XMLAttribute,
                  Elem => XMLElem,
                  Node => XMLNode,
                  UnprefixedAttribute}

import java.io.File

/** Some utility stuff.
  */
trait Util {
  /** Writes a string to a file, overwriting the file.
    *
    * @param path  the SBT path for the file to be written
    * @param str   the string to write
    */
  protected def writeStringToFile(path: RichFile, str: String): Unit =
    writeStringToFile(path.absolutePath, str)

  /** Writes a string to a file, overwriting the file.
    *
    * @param path  the path of the file to be written
    * @param str   the string to write
    */
  protected def writeStringToFile(path: String, str: String): Unit = {
    import java.io.FileWriter
    val writer = new FileWriter(path)
    writer.write(str + "\n")
    writer.flush()
    writer.close()
  }

  protected def temporaryFile(tempDir: File,
                              name: String,
                              ext: String): RichFile = {
    if (! tempDir.exists) {
      if (! tempDir.mkdirs)
        izError("Can't create directory \"%s\"" format tempDir)
    }

    val f = new RichFile(tempDir) / (name + ext)
    f.deleteOnExit()
    f
  }

  protected def izError(msg: String) = throw new IzPluginException(msg)

  /** Convert a boolean value to a "yes" or "no" string
    */
  protected def yesno(b: Boolean): String = if (b) "yes" else "no"

  /** Handle English variant spellings, mapping them into appropriate
    * IzPack-ese.
    */
  protected def adjustLicenseSpelling(s: String): String = {
    s.replaceAll("License", "Licence").
    replaceAll("license", "licence")
  }
}

/** Useful for constraining a value to one of a set of values.
  */
private[izpack] class ConstrainedValues(val values: Set[String],
                                        val default: String,
                                        val name: String)
extends Util {
  def apply(s: String): String = {
    if (values(s))
      s
    else
      izError("Bad name value of \"" + s + "\". Legal values: " +
              values.mkString(", "))
  }
}

/** An enhanced XML element class, with some useful utility methods.
  */
private[izpack] class EnhancedXMLElem(val elem: XMLElem) {
  def addAttributes(attrs: Seq[Tuple2[String, Option[String]]]): XMLElem = {
    @tailrec
    def doAdd(e: XMLElem, attrs: Seq[Tuple2[String, String]]): XMLElem = {
      attrs.toList match {
        case Nil =>
          e
        case (name, value) :: tail =>
          doAdd(e % new UnprefixedAttribute(name, value, XMLNode.NoAttributes),
                tail)
      }
    }

    doAdd(elem, attrs.filter(t => t._2 != None).map(t => (t._1, t._2.get)))
  }
}

/** Useful string methods
  */
private[izpack] class XString(val str: String) {
  /** Convenience method to check for a string that's null or empty.
    */
  def isEmpty = (str == null) || (str.trim == "")

  /** Convert the string to an option. An empty or null string
    * is converted to `None`.
    */
  def toOption = if (isEmpty) None else Some(str)
}

/** Various implicit conversions.
  */
private[izpack] object Implicits {
  implicit def stringToWrapper(s: String): XString = new XString(s)
  implicit def wrapperToString(is: XString): String = is.str

  implicit def xmlElemToWrapper(e: XMLElem): EnhancedXMLElem =
    new EnhancedXMLElem(e)
  implicit def wrapperToXMLElem(ee: EnhancedXMLElem): XMLElem = ee.elem
}

