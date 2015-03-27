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

import scala.annotation.tailrec

import scala.xml.{Comment => XMLComment,
                  Elem => XMLElem,
                  MetaData => XMLMetaData,
                  Node => XMLNode,
                  NodeSeq => XMLNodeSeq,
                  Null => XMLNull,
                  PrettyPrinter => XMLPrettyPrinter,
                  Text => XMLText,
                  TopScope => XMLTopScope,
                  UnprefixedAttribute,
                  XML}

import scala.beans.BeanProperty
import scala.collection.JavaConversions._
import scala.collection.mutable.{ListBuffer,
                                 Map => MutableMap,
                                 Set => MutableSet}
import scala.io.Source
import scala.util.matching.Regex
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.File
import java.util.{ArrayList => JArrayList, List => JList, HashMap => JHashMap}
import sbt.{Logger, Level => LogLevel, RichFile}
import grizzled.string.template.UnixShellStringTemplate
import grizzled.string.{util => StringUtil}
import grizzled.file.{util => FileUtil}

object MissingSection {
  def apply(name: String) = new XMLComment("No " + name + " section.")
}

case class SBTData(variables: Map[String, String], tempDir: File)

/** Implemented by config-related classes that can take an operating
  * system constraint. Assumes that the resulting XML can contain
  * an `<os family="osname"/>` element.
  */
trait OperatingSystemConstraints extends Util {
  import Constants._

  private val Legal = Set("windows", "macosx", "unix")

  private var operatingSystemsList: List[String] = Nil

  def setOs(osNames: String) = {
    val names = ListDelims.split(osNames).toList
    val bad = names.filterNot{ Legal(_) }
    if (! bad.isEmpty)
      izError("Bad operating system name(s): %s" format (bad mkString ", "))

    operatingSystemsList = names
  }

  def operatingSystems = operatingSystemsList

  def operatingSystemsToXML =
    operatingSystems.map(os => <os family={os}/>)
}

/** Keys used in option strings.
  */
private[izpack] trait OptionKeys {
  // Values must match what IzPack expects
  final val OS          = "os"
  final val Id          = "id"
  final val PackImgId   = "packimgid"
  final val Src         = "src"
  final val Target      = "target"
  final val TargetFile  = "targetfile"
  final val Dir         = "dir"
  final var TargetDir   = "targetdir"
  final val ParseType   = "type"
  final val Jar         = "jar"
  final val Condition   = "condition"
  final val ClassName   = "classname"
  final val Class       = "class"
  final val Stage       = "stage"
  final val Iso3        = "iso3"
  final var Name        = "name"
  final var Message     = "message"
  final var Description = "description"
  final var Encoding    = "encoding"
  final val ExecType    = "type"
  final val FailureType = "failure"
  final val Include     = "include"
  final val Exclude     = "exclude"
}

private[izpack] trait XMLable {
  
  def toXML: XMLElem

  def seqToXML(name: String, things: Seq[XMLable]) = {
    things.toList match {
      case Nil  => new XMLComment("No " + name + " sections")
      case list => list.map(_.toXML)
    }
  }
}

/** Maintains a map of string options.
  */
private[izpack] trait OptionStrings extends OptionKeys {
  import Implicits._

  private val options = MutableMap.empty[String, Option[String]]

  protected def hasOption(name: String): Boolean =
    options.get(name).map(s => true).getOrElse(false)

  protected def setOption(name: String, value: String) = {
    for (v <- Option(value) if v.trim.length > 0)
      options += name -> value.toOption
  }

  protected def getOption(name: String): Option[String] =
    options.getOrElse(name, None)

  protected def optionString(name: String): String =
    getOption(name).getOrElse("")

  protected def requiredStringOption(name: String,
                                     sectionName: String): Option[String] = {
    options.getOrElse(name, throw new MissingFieldException(name, sectionName))
  }

  protected def requiredString(name: String, sectionName: String): String =
    requiredStringOption(name, sectionName).get

  protected def strOptToXMLElement(name: String): XMLNode = {
    options.getOrElse(name, None).map {
      text => XMLElem(prefix        = null,
                      label         = name,
                      attributes    = XMLNode.NoAttributes,
                      scope         = XMLTopScope,
                      minimizeEmpty = false,
                      child         = XMLText(text))
    }.getOrElse(new XMLComment("No " + name + " element"))
  }
}

private[izpack] object ParseTypes
extends ConstrainedValues(Set("at", "ant", "java", "javaprop", "plain",
                              "shell", "xml"),
                          "plain", "parseType")

private[izpack] trait HasParseType extends OptionStrings {
  setOption(ParseType, ParseTypes.default)

  def setParseType(parseType: String): Unit =
    setOption(ParseType, ParseTypes(parseType))
}

private[izpack] trait IzPackSection extends OptionKeys with XMLable {
  import Implicits._

  /** XML for the section
    */
  protected def sectionToXML: XMLElem

  /** Contains any custom XML for the section. Typically supplied
    * by the writer of the build script.
    */
  var customXML: XMLNodeSeq = XMLNodeSeq.Empty

  /** Various setters and getters for the XML, including bean methods,
    * for the YAML parser.
    */
  def setCustomXML(xml: JArrayList[String]): Unit =
    customXML ++= xml.map {x => XML.loadString(x)}
  def getCustomXML: JArrayList[String] = null

  /** Generate the section's XML. Calls out to `sectionToXML`
    * and uses the contents of `customXML`.
    *
    * @return An XML element
    */
  def toXML: XMLElem = {
    // Append any custom XML to the end, as a series of child
    // elements.
    val node = sectionToXML
    val custom = new XMLComment("Custom XML") ++ customXML
    val allChildren = node.child ++ custom
    XMLElem(prefix        = node.prefix,
            label         = node.label,
            attributes    = node.attributes,
            scope         = node.scope,
            minimizeEmpty = false,
            child         = (allChildren: _*))
  }

  /** Create an empty XML node IFF a boolean flag is set. Otherwise,
    * return an XML comment.
    *
    * @param name   the XML element name
    * @param create the flag to test; if `true`, an XML element is created
    *
    * @return An XML node, either a node of the specified name or a comment
    */
  protected def maybeXML(name: String, create: Boolean): XMLNode =
    maybeXML(name, create, Map.empty[String,Option[String]])

  /** Create an empty XML node IFF a boolean flag is set. Otherwise,
    * return an XML comment.
    *
    * @param name   the XML element name
    * @param create the flag to test; if `true`, an XML element is created
    * @param attrs  name/value pairs for the XML attributes to attach
    *               to the element. An empty map signifies no attributes
    *
    * @return An XML node, either a node of the specified name or a comment
    */
  protected def maybeXML(name: String,
                         create: Boolean,
                         attrs: Map[String, Option[String]]): XMLNode = {
    def makeAttrs(attrs: List[(String, String)]): XMLMetaData = {
      attrs match {
        case (n, v) :: Nil if (v.isEmpty) =>
          XMLNull
        case (n, v) :: Nil =>
          new UnprefixedAttribute(n, v, XMLNode.NoAttributes)
        case (n, v) :: tail if (v.isEmpty) =>
          makeAttrs(tail)
        case (n, v) :: tail =>
          new UnprefixedAttribute(n, v, makeAttrs(tail))
        case Nil =>
          XMLNull
      }
    }

    if (! create)
      new XMLComment("No " + name + " element")

    else {
      val elem = XMLElem(prefix        = null,
                         label         = name,
                         attributes    = XMLNode.NoAttributes,
                         scope         = XMLTopScope,
                         minimizeEmpty = false,
                         child         = XMLText(""))
      elem addAttributes attrs.toSeq
    }
  }
}

private[izpack] object Constants{
  val ListDelims           = """[\s,]+""".r
  val IzPackVariableEscape = "@@@"
  val PluginBlurb          = "sbt-izpack (http://software.clapper.org/sbt-izpack/)"

}

/** The configuration parser.
  */
class IzPackYamlConfigParser(sbtData: SBTData,
                             variablesExportPrefixes: Seq[String],
                             logLevel: LogLevel.Value,
                             log: Logger) extends Util {
  import Constants._

  private class ConfigTemplate(variables: Map[String, String])
    extends UnixShellStringTemplate({n => Variables.get(n) },
                                          "[A-Za-z0-9][A-Za-z0-9_]+", true) {
    override def sub(name: String): Either[String, String] = {
      super.sub(name).right.map { _.replace("@@@", "$") }
    }
  }

  private val Variables = Map[String, String](
    // predefined IzPack variables to pass through

    "INSTALL_PATH"        -> (IzPackVariableEscape + "INSTALL_PATH")
  ) ++
  sbtData.variables

  def parse(source: Source): IzPackYamlConfig = {
    try {
      Globals.variables = Variables
      Globals.variablesExportPrefixes = variablesExportPrefixes
      Globals.sbtData = Some(sbtData)

      val yaml = new Yaml(new Constructor(classOf[IzPackYamlConfig]))
      val doc = preFilter(source.getLines.toList).mkString("\n")

      yaml.load(doc).asInstanceOf[IzPackYamlConfig]
    }

    catch {
      case e: Throwable => {
        val e2 = findCorrectException(e)
        if (logLevel == LogLevel.Debug)
          e2.printStackTrace()
        izError(e2.getMessage)
      }
    }
  }

  private def findCorrectException(e: Throwable): Throwable = {
    // Unwind the exceptions, to see if one of them is ours.

    @tailrec def doSearch(nextException: Throwable): Throwable = {
      val nested = nextException.getCause
      if (nested == null)
        e
      else if (nested.isInstanceOf[IzPluginException])
        nested
      else
        doSearch(nested)
    }

    doSearch(e)
  }

  private def preFilter(lines: List[String]): List[String] = {
    val template = new ConfigTemplate(Variables)

    @tailrec def sub(pre: List[String], post: List[String]):
      Either[String, List[String]] = {
      pre match {
        case Nil =>
          Right(post)

        case line :: tail => {
          template.sub(line) match {
            case Left(e) => Left(e)
            case Right(s) => sub(tail, post ::: List(s))
          }
        }
      }
    }

    sub(lines, Nil) match {
      case Left(e) => throw new Exception(s"Error during filtering phase: $e")
      case Right(s) => s
    }
  }
}

/** A regrettable global...
  */
private[izpack] object Globals {
  var variables = Map.empty[String,String]
  var variablesExportPrefixes = Seq.empty[String]
  var sbtData: Option[SBTData] = None
}

/** Base configuration class.
 */
private[izpack] class IzPackYamlConfig extends IzPackSection with Util {
  private var theInfo: Option[Info] = None
  private var languages: List[String] = Nil
  private var theResources: Option[Resources] = None
  private var thePackaging: Option[Packaging] = None
  private var theGuiPrefs: Option[GuiPrefs] = None
  private var thePanels: Option[Panels] = None
  private var thePacks: Option[Packs] = None
  private val theInstallerRequirements = new ListBuffer[InstallerRequirement]

  private lazy val dateFormatter =
    new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss")

  private def setOnce[T <: IzPackSection](target: Option[T],
                                          newValue: Option[T]): Option[T] = {
    (target, newValue) match {
      case (Some(t), None) =>
        None

      case (None, None) =>
        None

      case (Some(t), Some(v)) =>
        izError("Only one " + t.getClass.getSimpleName.toLowerCase +
                " item is permitted.")

      case (None, Some(v)) =>
        newValue
    }
  }

  private def setOnce[T <: IzPackSection](target: Option[T],
                                          newValue: T): Option[T] = {
    if (newValue == null)
      setOnce(target, None)
    else
      setOnce(target, Some(newValue))
  }

  def info: Option[Info] = theInfo
  def setInfo(info: Info): Unit = theInfo = setOnce(theInfo, info)

  def setPanels(p: Panels): Unit = thePanels = setOnce(thePanels, p)
  def panels: Option[Panels] = thePanels

  def setResources(r: Resources): Unit =
    theResources = setOnce(theResources, r)
  def resources: Option[Resources] = theResources

  def setPackaging(p: Packaging): Unit =
    thePackaging = setOnce(thePackaging, p)
  def packaging: Option[Packaging] = thePackaging

  def setGuiprefs(g: GuiPrefs): Unit =
    theGuiPrefs = setOnce(theGuiPrefs, g)
  def guiprefs: Option[GuiPrefs] = theGuiPrefs

  def setPacks(p: Packs): Unit = thePacks = setOnce(thePacks, p)
  def packs: Option[Packs] = thePacks

  def setInstallerRequirement(ir: InstallerRequirement): Unit =
    theInstallerRequirements += ir
  def installerRequirements = theInstallerRequirements.toList

  def setLanguages(langs: JList[String]): Unit =
    languages = langs.toList

  private def languagesToXML = {
    val langs = if (languages == Nil) List("eng") else languages

    <locale> {languages.map(name => <langpack iso3={name}/>)} </locale>
  }

  protected def sectionToXML = {
    val now = dateFormatter.format(new java.util.Date)

    <installation version="1.0">
      {XMLComment("IzPack installation file.")}
      {XMLComment(s"Generated by ${Constants.PluginBlurb}: $now")}
      {installerRequirementsToXML}
      {optionalSectionToXML(info, "Info")}
      {languagesToXML}
      {variablesToXML}
      {optionalSectionToXML(resources, "Resources")}
      {optionalSectionToXML(packaging, "Packaging")}
      {optionalSectionToXML(guiprefs, "GuiPrefs")}
      {optionalSectionToXML(panels, "Panels")}
      {optionalSectionToXML(packs, "Packs")}
    </installation>
  }

  private def variablesToXML = {
    import Constants._
    import Globals._

    <variables> {
      if (variables.size > 0) {
        // Skip escaped IzPack variables.
        variables.filter{ case (k, v) =>
          !v.startsWith(IzPackVariableEscape) && (
              variablesExportPrefixes.size == 0 ||
              variablesExportPrefixes.exists(k startsWith _)
          )
        }
        .map(kv => <variable name={kv._1} value={kv._2}/>)
      }
      else
        new XMLComment("No variables")
    }
    </variables>
  }

  private def installerRequirementsToXML = {
    def irXML = {
      <installerrequirements>
        {installerRequirements.map(_.toXML)}
      </installerrequirements>
    }

    installerRequirements match {
      case Nil => new XMLComment("no <installerrequirements>")
      case _   => irXML
    }
  }

  private def optionalSectionToXML(section: Option[IzPackSection],
                                   name: String) = {
    section.map (_.toXML).getOrElse(MissingSection(name))
  }

  def generateXML(out: File, log: Logger): Unit = {
    val parentDir = out.getParentFile
    log.debug("Creating " + parentDir)
    if ((! parentDir.exists) && (! parentDir.mkdirs()))
      izError("Can't create \"" + parentDir + "\"")

    log.debug("Generating " + out)
    val xml = toXML
    val prettyPrinter = new XMLPrettyPrinter(256, 2)
    writeStringToFile(out, prettyPrinter.format(xml))
    log.info("Done.")
  }
}

// ---------------------------------------------------------------------------
// Info section classes
// ---------------------------------------------------------------------------

private[izpack] class Author extends XMLable {
  @BeanProperty var name: String = ""
  var email: Option[String] = None

  def getEmail = email.getOrElse(null)
  def setEmail(s: String): Unit = email = Some(s)

  override def toString = email.map(e => name + " at " + e).getOrElse(name)

  def toMap = Map("name" -> name, "email" -> email.getOrElse(""))
  def toXML = <author name={name} email={email.getOrElse("")}/>
}

private[izpack] class RunPrivileged(yesno: Boolean) {
  import Implicits._

  @BeanProperty var uninstaller = false
  @BeanProperty var enabled = yesno

  // Condition examples:
  //    izpack.windowsinstall.vista
  //    izpack.windowsinstall.7|izpack.windowsinstall.vista
  var condition: Option[String] = None

  def this() = this(false)

  def setCondition(s: String): Unit = condition = s.toOption
}

private[izpack] class Info extends IzPackSection with OptionStrings with Util {
  @BeanProperty var createUninstaller = true
  @BeanProperty var requiresJDK = false
  @BeanProperty var runPrivileged = new RunPrivileged(false)
  @BeanProperty var pack200 = false
  @BeanProperty var writeInstallationInfo = true

  private val theAuthors = new ListBuffer[Author]

  private val JavaVersion = "javaversion"
  private val URL = "url"
  private val AppSubpath = "appsubpath"
  private val WebDir = "webdir"
  private val AppName = "appname"
  private val AppVersion = "appversion"
  private val SummaryLogFilePath = "summarylogfilepath"

  setOption(JavaVersion, "1.6")

  def setAuthor(a: Author): Unit = theAuthors += a
  def authors = theAuthors.toList

  def setSummaryLogFilePath(s: String): Unit =
    setOption(SummaryLogFilePath, s)
  def summaryLogFilePath = optionString(SummaryLogFilePath)

  def setUrl(u: String): Unit = setOption(URL, u)
  def url = optionString(URL)

  def setJavaVersion(v: String): Unit = setOption(JavaVersion, v)
  def javaVersion = optionString(JavaVersion)

  def setAppName(v: String): Unit = setOption(AppName, v)
  def appName = optionString(AppName)

  def setAppVersion(v: String): Unit = setOption(AppVersion, v)
  def appVersion  = optionString(AppVersion)

  def setAppSubPath(v: String): Unit = setOption(AppSubpath, v)
  def appSubPath = optionString(AppSubpath)

  def setWebdir(v: String): Unit = setOption(WebDir, v)
  def webdir = optionString(WebDir)

  protected def sectionToXML = {
    requiredStringOption(AppName, "info")

    <info>
      <appname>{appName}</appname>
      {strOptToXMLElement(AppVersion)}
      {strOptToXMLElement(JavaVersion)}
      {strOptToXMLElement(AppSubpath)}
      {strOptToXMLElement(URL)}
      {strOptToXMLElement(WebDir)}
      {strOptToXMLElement(SummaryLogFilePath)}
      {authorsToXML}
      <writeinstallationinformation>
        {yesno(writeInstallationInfo)}
      </writeinstallationinformation>
      <requiresjdk>{yesno(requiresJDK)}</requiresjdk>
      {maybeXML("uninstaller", createUninstaller,
                Map("write" -> Some("yes")))}
      {maybeXML("run-privileged", runPrivileged.enabled,
                Map("condition" -> runPrivileged.condition,
                    "uninstaller" -> Some(yesno(runPrivileged.uninstaller))))}
      {maybeXML("pack200", pack200)}
    </info>
  }

  private def authorsToXML = {
    authors match {
      case Nil => new XMLComment("no <authors>")
      case _   => <authors> {authors.map(_.toXML)} </authors>
    }
  }
}

// ---------------------------------------------------------------------------
// InstallerRequirements section
// ---------------------------------------------------------------------------

private[izpack] class InstallerRequirement
  extends IzPackSection
  with Util
  with OptionStrings {

  private val SectionName = "installerRequirement"

  def setCondition(v: String): Unit = setOption(Condition, v)
  def condition = optionString(Condition)

  def setMessage(v: String): Unit = setOption(Message, v)
  def message = optionString(Message)

  protected def sectionToXML = {
    <installerrequirement condition={requiredString(Condition, SectionName)}
                          message={requiredString(Message, SectionName)}/>
  }
}

// ---------------------------------------------------------------------------
// Resources section classes
// ---------------------------------------------------------------------------

private[izpack] class Resources extends IzPackSection {
  private var resources = new ListBuffer[Resource]
  private var installDirectories = new ListBuffer[InstallDirectory]

  def setResource(r: Resource): Unit = resources += r
  def setInstallDirectory(dir: InstallDirectory): Unit =
    installDirectories += dir

  protected def sectionToXML = {
    <resources>
      {resources.map(_.toXML)} {installDirectories.map(_.toXMLSeq).flatten}
    </resources>
  }
}

private[izpack] class Resource
  extends OptionStrings
  with Util
  with HasParseType
  with XMLable {

  private val SectionName = "resource"

  import Implicits._

  @BeanProperty var parse: Boolean = false

  // Don't adjust license spelling here. Yeah, IzPack isn't consistent.
  def setId(id: String): Unit = setOption(Id, id)
  def setSrc(src: String): Unit = setOption(Src, src)
  def setEncoding(e: String): Unit = setOption(Encoding, e)

  def toXML = {
    val id = requiredString(Id, SectionName)
    val src = new File(FileUtil.nativePath(requiredString(Src, SectionName)))
    if (! src.exists)
      izError("Resource %s has \"src\" value \"%s\", but that file " +
              "does not exist." format (id, src))

    val elem =
      <res id={id} src={src.getAbsolutePath} parse={yesno(parse)}
           type={requiredString(ParseType, SectionName)}/>
    elem addAttributes Seq(("encoding", getOption(Encoding)))
  }
}

private[izpack] class InstallDirectory
  extends Util
  with OperatingSystemConstraints {

  import Globals._

  private var path: Option[String] = None

  def setPath(s: String): Unit = path = Some(FileUtil.nativePath(s))

  def toXMLSeq = {
    for (os <- operatingSystems)
      yield <res id={"TargetPanel.dir." + os} src={installDirString(os)}/>
  }

  private def installDirString(os: String): String = {
    val instPath = path.getOrElse(
      izError("No path setting in installDirectory section.")
    )
    val filename = temporaryFile(sbtData.get.tempDir, "instdir_" + os, ".txt")
    // Put the string in the file. That's how IzPack wants it.
    writeStringToFile(filename, instPath)
    filename.absolutePath
  }
}

// ---------------------------------------------------------------------------
// Packaging classes
// ---------------------------------------------------------------------------

private[izpack] class Packaging extends IzPackSection with Util {
  object Packager extends Enumeration {
    private val packageName = "com.izforge.izpack.compiler"
    type Packager = Value

    val SingleVolume = Value(packageName + ".Packager")
    val MultiVolume = Value(packageName + ".MultiVolumePackager")
  }

  private val PackagerValues = Map(
    "single-volume" -> Packager.SingleVolume,
    "multi-volume"  -> Packager.MultiVolume
  )

  import Packager._

  var packager: Option[Packager] = None
  @BeanProperty var volumeSize: Int = 0
  @BeanProperty var firstVolumeFreeSpace: Int = 0

  def setPackager(value: String): Unit = {
    if (packager != None)
      izError("Only one packager value is permitted.")
    packager = Some(PackagerValues.getOrElse(
      value, izError("Unknown packager value: %s" format value)
    ))
  }

  protected def sectionToXML = {
    val p = packager.getOrElse(Packager.SingleVolume)
    if ((p != MultiVolume) && ((volumeSize + firstVolumeFreeSpace) > 0)) {
      izError("volumeSize and firstVolumeFreeSpace are " +
              "ignored unless packager is MultiVolume.")
    }

    val (packagingXML, unpacker) = p match {
      case MultiVolume => (
        <options volumesize={volumeSize.toString}
                 firstvolumefreespace={firstVolumeFreeSpace.toString}/>,
        "com.izforge.izpack.installer.MultiVolumeUnpacker"
      )

        case SingleVolume => (
          new XMLComment("no options"), "com.izforge.izpack.installer.Unpacker"
        )
    }

    <packaging>
      <packager class={p.toString}>packagingXML</packager>
      <unpacker class={unpacker}/>
    </packaging>
  }
}

// ---------------------------------------------------------------------------
// Guiprefs classes
// ---------------------------------------------------------------------------

private[izpack] class GuiPrefs extends IzPackSection with Util {
  @BeanProperty var height: Int = 600
  @BeanProperty var width: Int = 800
  @BeanProperty var resizable: Boolean = true

  private var lafs = new ListBuffer[LookAndFeel]

  def setLaf(map: JHashMap[String, String]): Unit = {
    val laf = map.toMap
    val name = laf.getOrElse(
      "name", izError("Missing required name for LAF.")
    )
    val os = laf.getOrElse("os", "")
    val params = laf - "name" - "os"
    lafs += new LookAndFeel(name, os, params)
  }

  protected def sectionToXML = {
    val strResizable = if (resizable) "yes" else "no"

    <guiprefs height={height.toString} width={width.toString}
              resizable={yesno(resizable)}> {lafs.map(_.toXML)}
    </guiprefs>
  }
}

private[izpack] class LookAndFeel(val name: String,
                                  osNames: String,
                                  val params: Map[String, String])
extends IzPackSection with OperatingSystemConstraints {
  setOs(osNames)

  protected def sectionToXML = {
    <laf name={name}>
      {operatingSystemsToXML}
      {params.map(kv => <param name={kv._1} value={kv._2}/>)}
    </laf>
  }
}

// ---------------------------------------------------------------------------
// Panels classes
// ---------------------------------------------------------------------------

private[izpack] class Panels extends IzPackSection {
  private val panels = new ListBuffer[Panel]

  def setPanel(panel: Panel): Unit = panels += panel

  protected def sectionToXML = {

    if (panels.exists(_ == null)) {
      throw new IzConfigException("Empty panel section(s) in YAML.")
    }

    <panels> {panels.map(_.toXML)} </panels>
  }
}

/** A single panel.
  */
private[izpack] class Panel extends IzPackSection with OptionStrings with Util {
  private val SectionName = "panel"

  private var jar: Option[RichFile] = None
  private var help = new ListBuffer[Help]
  private var actions = new ListBuffer[Action]
  private var validators = new ListBuffer[Validator]

  def setClassName(name: String): Unit = setOption(ClassName, name)
  def setId(id: String): Unit = setOption(Id, id)
  def setCondition(s: String): Unit = setOption(Condition, s)
  def setHelp(h: Help): Unit = help += h
  def setAction(a: Action): Unit = actions += a
  def setValidator(v: Validator): Unit = validators += v

  /** Allows assignment of `jar` field
    */
  def setJar(p: String): Unit =
    jar = Some(new RichFile(new File(p)))

  protected def sectionToXML = {
    import Implicits._

    val elem =
      <panel classname={requiredString(ClassName, SectionName)}>
        {seqToXML("help", help.toList)}
        {seqToXML("validators", validators.toList)}
	    <actions>  
            {seqToXML("actions", actions.toList)}
	    </actions>
      </panel>

    elem addAttributes Seq(("jar", jar.map(_.absolutePath)),
                           ("id", getOption(Id)),
                           ("condition", getOption(Condition)))
  }
}

private[izpack] class Help extends OptionStrings with Util with XMLable {
  private val SectionName = "help"

  def setIso3(s: String): Unit = setOption(Iso3, s)
  def setSrc(s: String): Unit = setOption(Iso3, s)

  def toXML = {
    val iso3 = requiredString(Iso3, SectionName)
    val src = new File(FileUtil.nativePath(requiredString(Src, SectionName)))
    if (! src.exists)
      izError("Help %s has \"src\" value \"%s\", but that file " +
              "does not exist." format (iso3, src))

    <help iso3={iso3} src={src.getAbsolutePath}/>
  }
}

/** Validators
  */
private[izpack] class Validator extends OptionStrings with XMLable {
  def setClassName(s: String): Unit = setOption(ClassName, s)
  def toXML = <validator classname={requiredString(ClassName, "validator")}/>
}

/** Embedded actions.
  */
private[izpack] class Action extends OptionStrings with XMLable {
  private val SectionName = "action"

  private object StageTypes extends ConstrainedValues(
    Set("preconstruct", "preactivate", "prevalidate", "postvalidate"),
    "preconstruct", "stage"
  )

  def setStage(s: String): Unit = setOption(Stage, StageTypes(s))
  def setClassName(s: String): Unit = setOption(ClassName, s)

  def toXML =
    <action stage={requiredString(Stage, SectionName)}
            classname={requiredString(ClassName, SectionName)}/>
}

// ---------------------------------------------------------------------------
// Packs classes
// ---------------------------------------------------------------------------

private[izpack] class Packs extends IzPackSection with Util {
  private var packs = new ListBuffer[Pack]

  def setPack(pack: Pack): Unit = packs += pack

  protected def sectionToXML = {
    <packs>
      {seqToXML("packs", packs.toList)}
    </packs>
  }
}

private[izpack] class Pack extends IzPackSection with OperatingSystemConstraints
with Util with OptionStrings {
  import Implicits._

  private val SectionName = "pack"

  private var files = new ListBuffer[OneFile]
  private var filesets = new ListBuffer[FileSet]
  private var fsets = new ListBuffer[FSet]
  private var executables = new ListBuffer[Executable]
  private var parsables = new ListBuffer[Parsable]
  private var updateCheck: Option[UpdateCheck] = None
  private var depends = new ListBuffer[String]

  @BeanProperty var required: Boolean = false
  @BeanProperty var loose: Boolean = false
  @BeanProperty var hidden: Boolean = false
  @BeanProperty var preselected: Boolean = false

  def setName(name: String): Unit = setOption(Name, name)
  def setId(id: String): Unit = setOption(Id, id)
  def setPackImgId(id: String): Unit = setOption(PackImgId, id)
  def setDescription(s: String): Unit = setOption(Description, s)
  def setDepends(s: String): Unit = depends += s
  def setFile(s: FileOrDirectory): Unit = files += s
  def setSingleFile(s: SingleFile): Unit = files += s
  def setFileset(s: FileSet): Unit = filesets += s
  def setFset(s: FSet): Unit = fsets += s
  def setExecutable(e: Executable): Unit = executables += e
  def setUpdateCheck(u: UpdateCheck): Unit = updateCheck = Some(u)
  def setParsable(p: Parsable): Unit = parsables += p

  protected def sectionToXML = {
    val elem =
      <pack name={requiredString(Name, SectionName)}
                  required={yesno(required)}
                  loose={loose.toString}
                  hidden={hidden.toString}
                  preselected={yesno(preselected)}>
        <description>{optionString(Description)}</description>
        {operatingSystemsToXML}
        {depends.map(s => <depends packname={s}/>)}
        {files.map(_.toXML)} {filesets.map(_.toXMLSeq).flatten}
	    {fsets.map(_.toXML)}
        {seqToXML("parsable", parsables.toList)}
        {seqToXML("executables", executables.toList)}
        {updateCheck.getOrElse(new XMLComment("no updatecheck"))}
      </pack>

    elem addAttributes Seq(("packImgId", getOption(PackImgId)))
  }
}

private[izpack] object OverrideValues extends Util {
  val Legal = Set("true", "false", "asktrue", "askfalse", "update")

  def apply(s: String): String =
    if (Legal(s)) s else izError("Bad override value: \"%s\"" format s)

  def default = "update"
}

private[izpack] trait Overridable extends OptionStrings {
  protected var overrideValue = OverrideValues.default

  def setOverride(s: String): Unit = overrideValue = OverrideValues(s)
}

private[izpack] trait OneFile extends IzPackSection
with OperatingSystemConstraints with OptionStrings with Overridable with Util {
  val SectionName: String

  def setDir(s: String): Unit =
    if (new File(s).exists)
      setOption(Dir, s)
    else
      izError("dir \"%s\" does not exist." format s)

  def setSrc(s: String): Unit = setOption(Src, s)
  def setCondition(v: String): Unit = setOption(Condition, v)

  protected def srcPath = {
    val file = new File(requiredString(Src, SectionName))
    if (! file.exists)
      izError("src \"" + file.getAbsolutePath + "\" does not exist.")

    file.getAbsolutePath
  }
}

private[izpack] class SingleFile extends OneFile with Util{
  import Implicits._

  val SectionName = "singleFile"

  def setTargetFile(s: String): Unit = setOption(TargetFile, s)

  protected def sectionToXML = {
    val elem =
      <singlefile src={srcPath}
                  target={requiredString(TargetFile, SectionName)}
                  override={overrideValue}>
        {operatingSystemsToXML}
      </singlefile>

    elem addAttributes Seq(("condition", getOption(Condition)))
  }
}

private[izpack] class FileOrDirectory extends OneFile with Util {
  import Implicits._

  val SectionName = "file"

  @BeanProperty var unpack: Boolean = false

  def setTargetDir(s: String): Unit = setOption(TargetDir, s)

  protected def sectionToXML = {
    val elem =
      <file src={srcPath}
            targetdir={requiredString(TargetDir, SectionName)}
            unpack={yesno(unpack)}
            override={overrideValue}>
        {operatingSystemsToXML}
      </file>

    elem addAttributes Seq(("condition", getOption(Condition)))
  }
}

private[izpack] class FSet
	extends IzPackSection
	with OperatingSystemConstraints
	with Util
	with Overridable {

	import Constants._

	private val SectionName = "fset"

	def setDir(dir: String) {
		//if (!new File(dir).isDirectory)
		//	izError(s"fset/dir invalid: $dir")
		setOption(Dir, dir)
	}
	def setTargetdir(path: String) { setOption(TargetDir, path) }
	def setIncludes(v: String) { setOption(Include, v) }
	def setExcludes(v: String) { setOption(Exclude, v) }

	def sectionToXML = {
		val inc = optionString(Include)
		<fileset
			dir={requiredString(Dir, SectionName)}
			targetdir={requiredString(TargetDir, SectionName)}
			includes={if (inc == "") "**/*" else inc}
			excludes={optionString(Exclude)}
			override={overrideValue} >
			{operatingSystemsToXML}
		</fileset>
	}

	override def toString =
		s"""fset[
	        |s"dir=<${optionString(Dir)}>
	        |s"targetdir=<${optionString(TargetDir)}>
			|s"includes=<${optionString(Include)}>
			|s"excludes=<${optionString(Exclude)}>]""".stripMargin
}

private[izpack] class FileSet
  extends OperatingSystemConstraints
  with Util
  with OptionStrings
  with Overridable {

  import Constants._
  import Implicits._

  private val SectionName = "fileset"

  private var includes = MutableSet[String]()
  private var excludes = MutableSet[String]()
  // Purely for matching
  private var regexExcludes = MutableSet[Regex]()

  @BeanProperty var caseSensitive: Boolean = false

  def setIncludes(patternList: String): Unit = {
    for (p <- Option(patternList) if p.trim.length > 0) {
      for (pattern <- ListDelims.split(patternList)) {
        includes ++= FileUtil.eglob(pattern).toSet
      }
    }
  }

  def setExcludes(patternList: String): Unit = {
    for (p <- Option(patternList) if p.trim.length > 0) {
      for (pattern <- ListDelims.split(p)) {
        excludes ++= FileUtil.eglob(pattern).toSet
      }
    }
  }

  def setRegexExcludes(regexList: String): Unit = {
    for (r <- Option(regexList) if r.trim.length > 0) {
      for (reString <- ListDelims.split(regexList)) {
        regexExcludes += reString.r
      }
    }
  }

  def setTargetDir(path: String): Unit = setOption(TargetDir, path)
  def setCondition(s: String): Unit = setOption(Condition, s)

  def toXMLSeq = {
    // NOTES:
    // 1. &~ is set difference
    // 2. XML Node objects appear to hash weirdly, so convert the set to a
    //    list, so we don't lose elements in the mapping.
    filterRegexExcludes((includes &~ excludes).toSet).toList.map(fileToXML(_))
  }

  private def filterRegexExcludes(paths: Set[String]) = {
    paths.filterNot { path =>
      regexExcludes.exists { _.findFirstIn(path).isDefined }
    }
  }

  private def fileToXML(path: String) = {
    val elem =
      <file src={new File(path).getAbsolutePath}
            targetdir={requiredString(TargetDir, SectionName)}
            casesensitive={yesno(caseSensitive)}
            override={overrideValue}>
        {operatingSystemsToXML}
      </file>

    elem addAttributes Seq(("condition", getOption(Condition)))
  }

  override def toString = {
    s"FileSet[includes=<${includes.mkString(", ")}> " +
    s"excludes=<${excludes.mkString(", ")}> " +
    s"regexExcludes=<${regexExcludes.mkString(", ")}>]"
  }
}

private[izpack] class UpdateCheck extends OptionStrings with Util with XMLable {
  private val SectionName = "updateCheck"

  def setInclude(s: String): Unit = setOption(Include, s)
  def setExclude(s: String): Unit = setOption(Exclude, s)

  def toXML = {
    <updatecheck>
      <include name={requiredString(Include, SectionName)}/> {
        if (hasOption(Exclude))
          <exclude name={optionString(Exclude)}/>
        else
          new XMLComment("no excludes")
      }
    </updatecheck>
  }
}

private[izpack] class Executable extends IzPackSection
with OperatingSystemConstraints with OptionStrings with Util {
  import Implicits._

  private val SectionName = "executable"

  private[izpack] object FailureTypes
  extends ConstrainedValues(Set("abort", "ask", "warn"), "warn", "failureType")
  private[izpack] object ExecutableType
  extends ConstrainedValues(Set("bin", "jar"), "bin", "executableType")
  private object StageTypes
  extends ConstrainedValues(Set("postinstall", "never", "uninstall"),
                            "never", "stage")

  @BeanProperty var keep: Boolean = false
  private var args = new ListBuffer[String]()

  setOption(FailureType, FailureTypes.default)
  setOption(Stage, StageTypes.default)

  def setTargetFile(s: String): Unit = setOption(TargetFile, s)
  def setClass(s: String): Unit = setOption(Class, s)
  def setExecutableType(s: String): Unit = setOption(ExecType, s)
  def setFailure(s: String): Unit = setOption(FailureType, FailureTypes(s))
  def setStage(s: String): Unit = setOption(Stage, StageTypes(s))
  def setArg(s: String): Unit = args += s

  protected def sectionToXML = {
    val elem =
      <executable targetfile={requiredString(TargetFile, SectionName)}
                  stage={requiredString(Stage, SectionName)}
                  keep={keep.toString}
                  failure={requiredString(FailureType, SectionName)}>
        {operatingSystemsToXML} {
          if (args.length == 0)
            new XMLComment("No args")
          else
            args.map(a => <arg value={a}/>)
        }
      </executable>

    elem addAttributes Seq(("class",     getOption(Class)),
                           ("type",      getOption(ExecType)),
                           ("condition", getOption(Condition)))
  }
}

private[izpack] class Parsable extends IzPackSection
with OperatingSystemConstraints with OptionStrings with HasParseType {
  import Implicits._

  private val SectionName = "parsable"

  private var parseType: String = ParseTypes.default

  def setTargetFile(s: String): Unit = setOption(TargetFile, s)
  def setEncoding(s: String): Unit = setOption(Encoding, s)
  def setCondition(s: String): Unit = setOption(Condition, s)

  protected def sectionToXML = {
    val elem =
      <parsable targetfile={requiredString(TargetFile, SectionName)}
                type={requiredString(ParseType, SectionName)}>
        {operatingSystemsToXML}
      </parsable>

    elem addAttributes Seq(("condition", getOption(Condition)),
                           ("encoding",  getOption(Encoding)))
  }
}

