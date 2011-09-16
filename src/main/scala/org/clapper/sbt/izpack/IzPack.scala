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

import sbt._
import Keys._
import Defaults._
import Project.Initialize
import com.izforge.izpack.compiler.CompilerConfig
import scala.io.Source
import java.io.File
import scala.collection.mutable.{Map => MutableMap}
import grizzled.file.{util => FileUtil}

case class Metadata(installSourceDir: RichFile,
                    baseDirectory: RichFile,
                    scalaVersion: String,
                    private[sbt] val updateReport: UpdateReport)

/** Plugin for SBT (Simple Build Tool) to configure and build an IzPack
  * installer.
  */
object IzPack extends Plugin {
  // -----------------------------------------------------------------
  // Plugin Settings and Tasks
  // -----------------------------------------------------------------

  // NOTE: Using an inner object, with "in Config" after the Setting
  // or Task definition allows this usage pattern:
  //
  //     IzPack.configFile <<= ...
  //
  // The "in Config" trick is courtesy the masterful Josh Suereth.
  object IzPack {
    val Config = config("izpack") extend(Runtime)

    val configFile = SettingKey[File]("config-file") in Config
    val installerJar = SettingKey[RichFile]("installer-jar") in Config

    val installSourceDir = SettingKey[File](
      "install-source-dir",
      "Directory containing auxiliary installer source files."
    ) in Config

    val installXML = SettingKey[File](
      "install-xml", "Path to the generated XML file."
    ) in Config

    val variables = SettingKey[Seq[Tuple2[String, String]]](
      "variables", "Additional variables for substitution in the config"
    ) in Config

    val tempDirectory = SettingKey[File](
      "temp-dir", "Where to generate temporary installer files."
    ) in Config

    val logLevel = SettingKey[Level.Value](
      "log-level", "Log level within sbt-izpack"
    ) in Config

    val createXML = TaskKey[RichFile](
      "create-xml", "Create IzPack XML"
    ) in Config

    val createInstaller = TaskKey[Unit](
      "create-installer", "Create IzPack installer"
    ) in Config

    val clean = TaskKey[Unit]("clean", "Remove target files.") in Config

    val predefinedVariables = TaskKey[Map[String, String]](
      "predefined-variables", "Predefined sbt-izpack variables"
    ) in Config

    val captureSettings1 = TaskKey[Map[String,String]](
      "-capture-settings-1",
      "Don't mess with this. Seriously. If you do, you'll break the plugin."
    )

    val captureSettings2 = TaskKey[Map[String,String]](
      "-capture-settings-2",
      "Don't mess with this. Seriously. If you do, you'll break the plugin."
    )
  }

  val izPackSettings: Seq[sbt.Project.Setting[_]] = inConfig(IzPack.Config)(Seq(

    IzPack.installerJar <<= baseDirectory(_ / "target" / "installer.jar"),
    IzPack.installSourceDir <<= baseDirectory(_ / "src" / "izpack"),
    IzPack.installXML <<= baseDirectory(_ / "target" / "izpack.xml"),
    IzPack.configFile <<= IzPack.installSourceDir(_ / "izpack.yml"),
    IzPack.tempDirectory <<= baseDirectory(_ / "target" / "installtmp"),
    IzPack.variables := Nil,
    IzPack.logLevel := Level.Info,

    IzPack.predefinedVariables <<= predefinedVariablesTask,
    IzPack.captureSettings1 <<= captureSettingsTask1,
    IzPack.captureSettings2 <<= captureSettingsTask2,
    IzPack.createXML <<= createXMLTask,
    IzPack.createInstaller <<= createInstallerTask,
    IzPack.clean <<= cleanTask
  )) ++
  inConfig(Compile)(Seq(
    // Hook our clean into the global one.
    clean in Global <<= (IzPack.clean in IzPack.Config).identity,

    (IzPack.createXML in IzPack.Config) <<=
      (IzPack.createXML in IzPack.Config).dependsOn(
        packageBin in Compile
      )
  ))

  // -----------------------------------------------------------------
  // Methods
  // -----------------------------------------------------------------

  private def allDependencies(updateReport: UpdateReport) =
    updateReport.allFiles.map(_.absolutePath).mkString(", ")

  private def predefinedVariablesTask = {
    (IzPack.captureSettings1) map { m => m }
  }

  private def captureSettingsTask1 = {
    (update, libraryDependencies, (classDirectory in Compile), name, target,
     normalizedName, version, scalaVersion, IzPack.captureSettings2) map {

      (updateReport, libraryDependencies, classDirectory, name, target,
       normalizedName, version, scalaVersion, settingsMap) =>

      val classesParent = classDirectory.getParentFile.getAbsolutePath
      val jarName = "%s_%s-%s.jar" format (normalizedName,
                                           scalaVersion,
                                           version)
      val appJar = FileUtil.joinPath(classesParent, jarName)
      val allDeps: Seq[(String, ModuleID, Artifact, File)] = updateReport.toSeq
      val allDepFiles = allDeps.map(tuple => tuple._1)

      // Using allDeps, map the library dependencies to their resolved file
      // names.

      val filteredLibDeps = allDeps.filter {tuple =>
        val (s, module, artifact, file) = tuple
        val matched = libraryDependencies.filter { l =>

          // Sometimes, one starts with a prefix of the other (e.g.,
          // libfoo vs. libfoo_2.8.1)
          (module.name.startsWith(l.name) || l.name.startsWith(module.name)) &&
          (l.organization == module.organization)
        }

        matched.length > 0
      }

      // filteredLibDeps is a tuple: (s, module, artifact, file)
      val libDepFiles = filteredLibDeps.map {_._4}.distinct

      settingsMap ++ Seq(
        "appName"             -> name,
        "appVersion"          -> version,
        "normalizedAppName"   -> normalizedName,
        "scalaVersion"        -> scalaVersion,
        "target"              -> target.absolutePath,
        "appJar"              -> appJar,
        "classDirectory"      -> classDirectory.absolutePath,
        "allDependencies"     -> updateReport.allFiles.map{_.absolutePath}.
                                 mkString(", "),
        "libraryDependencies" -> libDepFiles.mkString(", ")
      )
    }
  }

  private def captureSettingsTask2 = {
    (baseDirectory, IzPack.installSourceDir) map {(base, installSourceDir) =>

      Map.empty[String,String] ++ Seq(
        "baseDirectory"       -> base.absolutePath,
        "installSourceDir"    -> installSourceDir.absolutePath
      )
    }
  }

  private def cleanTask: Initialize[Task[Unit]] = {
    import grizzled.file.GrizzledFile._

    (IzPack.installXML, IzPack.tempDirectory, streams) map {
      (installXML, tempDirectory, streams) =>

      if (installXML.exists) {
        streams.log.debug("Deleting \"%s\"" format installXML)
        installXML.delete
      }

      if (tempDirectory.exists) {
        streams.log.debug("Deleting \"%s\"" format tempDirectory)
        tempDirectory.deleteRecursively
      }
    }
  }

  private def createXMLTask = {
    (IzPack.configFile, IzPack.installXML, IzPack.variables,
     IzPack.predefinedVariables, IzPack.tempDirectory, IzPack.logLevel,
     streams) map {

      (configFile, installXML, variables, predefinedVariables,
       tempdir, logLevel, streams) =>

      createXML(configFile, variables, predefinedVariables, installXML,
                tempdir, logLevel, streams.log)
    }
  }

  private def createInstallerTask = {
    (IzPack.createXML, IzPack.installerJar, streams) map {
      (xml, outputJar, streams) =>

      makeInstaller(xml, outputJar, streams.log)
      ()
    }
  }

  private def createXML(configFile: File,
                        variables: Seq[Tuple2[String, String]],
                        predefinedVariables: Map[String, String],
                        installXML: RichFile,
                        tempDirectory: File,
                        logLevel: Level.Value,
                        log: Logger): RichFile = {
    val allVariables = predefinedVariables ++ variables
    val sbtData = new SBTData(allVariables, tempDirectory)
    val parser = new IzPackYamlConfigParser(sbtData, logLevel, log)
    val izConfig = parser.parse(Source.fromFile(configFile))

    // Create the XML.

    val path = installXML.absolutePath
    log.info("Generating IzPack XML \"%s\"" format path)
    izConfig.generateXML(installXML.asFile, log)
    log.info("Created " + path)
    installXML
  }

  /** Build the actual installer jar.
    *
    * @param izPackXML  the IzPack installer XML configuration
    * @param outputJar  where to store the installer jar file
    */
  private def makeInstaller(izPackXML: RichFile,
                            outputJar: RichFile,
                            log: Logger) = {
    IO.withTemporaryDirectory {baseDir =>

      log.info("Generating IzPack installer")
      val compilerConfig = new CompilerConfig(izPackXML.absolutePath,
                                              baseDir.getPath, // basedir
                                              CompilerConfig.STANDARD,
                                              outputJar.absolutePath)
      log.info("Created installer in " + outputJar.absolutePath)
      compilerConfig.executeCompiler
    }
  }
}
