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

import com.izforge.izpack.compiler.CompilerConfig
import sbt._
import sbt.Keys._
import xsbti.compile.CompileProgress
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
object IzPackPlugin extends AutoPlugin {

  override def trigger = allRequirements

  object autoImport { // Stuff that's autoimported into the user's build.sbt

    lazy val IzPack = config("izpack")

    val izpack = taskKey[File]("Generate IzPack XML configuration file.")

    val configFile = SettingKey[File]("config-file", "YAML configuration file")

    val installerJar = SettingKey[File](
      "installer-jar", "Installer JAR file to generate"
    )

    val installSourceDir = SettingKey[File](
      "install-source-dir",
      "Directory containing auxiliary installer source files."
    )

    val installXML = SettingKey[File](
      "install-xml", "Path to the generated XML file."
    )

    val variables = SettingKey[Seq[(String, String)]](
      "variables", "Additional variables for substitution in the config"
    )

    val tempDirectory = SettingKey[File](
      "temp-dir", "Where to generate temporary installer files."
    )

    val createXML = TaskKey[RichFile](
      "create-xml", "Create IzPack XML"
    )

    val createInstaller = TaskKey[Unit](
      "create-installer", "Create IzPack installer"
    )

    val cleanIzPack = TaskKey[Unit](
      "clean", "Clean up the IzPack-generated files"
    )

    val predefinedVariables = TaskKey[Map[String, String]](
      "predefined-variables", "Predefined sbt-izpack variables"
    )

    val captureSettings = TaskKey[Map[String,String]](
      "-capture-settings", "For internal use only."
    )

    lazy val baseIzPackSettings: Seq[Def.Setting[_]] = Seq(
      installerJar              := baseDirectory(_ / "target" / "installer.jar").value,
      installSourceDir          := baseDirectory(_ / "src" / "izpack").value,
      installXML                := baseDirectory(_ / "target" / "izpack.xml").value,
      configFile                := baseDirectory(_ / "src" / "izpack" / "izpack.yml").value,
      tempDirectory             := baseDirectory(_ / "target" / "installtmp").value,
      variables                 := Nil,
      logLevel                  := Level.Info,
      createXML in IzPack       := IzPackRunner.createInstallXML(IzPack).value,
      createInstaller in IzPack := IzPackRunner.createInstaller(IzPack).value,
      clean in IzPack           := IzPackRunner.clean(IzPack).value,
      captureSettings in IzPack := IzPackRunner.retrieveSettings(IzPack).value
    )
  }

  import autoImport._

  override lazy val projectSettings = inConfig(IzPack)(baseIzPackSettings)

  object IzPackRunner {
    def createInstallXML(config: Configuration):
      Def.Initialize[Task[File]] = Def.task {

      val inputYaml           = (configFile in config).value
      val settings            = (captureSettings in config).value
      val xmlFile             = (installXML in config).value
      val vars                = (variables in config).value
      val predefinedVariables = (captureSettings in config).value
      val tempDir             = (tempDirectory in config).value
      val level               = (logLevel in config).value
      val pkgBin              = (packageBin in Compile).value
      val log                 = streams.value.log

      val allVariables = predefinedVariables ++ vars
      val sbtData      = new SBTData(allVariables, tempDir)
      val parser       = new IzPackYamlConfigParser(sbtData, level, log)
      val izConfig     = parser.parse(Source.fromFile(inputYaml))

      // Create the XML.

      val path = xmlFile.absolutePath
      log.info("Generating IzPack XML \"%s\"" format path)
      izConfig.generateXML(xmlFile.asFile, log)
      log.info("Created " + path)
      xmlFile
    }

    def createInstaller(config: Configuration):
      Def.Initialize[Task[File]] = Def.task {

      IO.withTemporaryDirectory { baseDir =>
        val jar     = (installerJar in config).value
        val xmlFile = (createXML in config).value
        val log     = streams.value.log

        log.info("Generating IzPack installer")
        val compilerConfig = new CompilerConfig(xmlFile.absolutePath,
                                                baseDir.getPath,
                                                CompilerConfig.STANDARD,
                                                jar.getAbsolutePath)
        log.info(s"Created installer in ${jar.absolutePath}")
        compilerConfig.executeCompiler

        jar
      }
    }

    def clean(config: Configuration): Def.Initialize[Task[Unit]] = Def.task {
      import grizzled.file.GrizzledFile._

      val xmlFile = (installXML in config).value
      val tempDir = (tempDirectory in config).value
      val str = streams.value

      if (xmlFile.exists) {
        str.log.debug("Deleting \"%s\"" format installXML)
        xmlFile.delete
      }

      if (tempDir.exists) {
        str.log.debug("Deleting \"%s\"" format tempDirectory)
        tempDir.deleteRecursively
      }
    }

    // Capture settings from the project. Must be a task; otherwise, the
    // various mappings (and calls to .value) won't compile.
    private[izpack] def retrieveSettings(config: Configuration):
      Def.Initialize[Task[Map[String, String]]] = Def.task {

      val updateReport = update.value
      val libDeps = libraryDependencies.value
      val classDir = (classDirectory in Compile).value
      val targetDir = target.value
      val projectVersion = version.value
      val scalaV = scalaVersion.value

      val seedSettings = Map(
        "baseDirectory"       -> baseDirectory.value.absolutePath,
        "installSourceDir"    -> installSourceDir.value.absolutePath
      )

      val classesParent = classDir.getParentFile.getAbsolutePath
      val jarName = "%s_%s-%s.jar" format (normalizedName.value,
                                           scalaVersion.value,
                                           version)
      val appJar = FileUtil.joinPath(classesParent, jarName)
      val allDeps: Seq[(String, ModuleID, Artifact, File)] = updateReport.toSeq
      val allDepFiles = allDeps.map(tuple => tuple._1)

      // Using allDeps, map the library dependencies to their resolved file
      // names.

      val filteredLibDeps = allDeps.filter {
        case (s, module, artifact, file) => {
          val matched = libraryDependencies.value.filter { l =>

            // Sometimes, one starts with a prefix of the other (e.g.,
            // libfoo vs. libfoo_2.8.1)
            (module.name.startsWith(l.name) || l.name.startsWith(module.name)) &&
              (l.organization == module.organization)
          }

          matched.length > 0
        }
      }

      // filteredLibDeps is a tuple: (s, module, artifact, file)
      val libDepFiles = filteredLibDeps.map {_._4}.distinct

      seedSettings ++ Seq(
        "appName"             -> name.value,
        "appVersion"          -> version.value,
        "normalizedAppName"   -> normalizedName.value,
        "scalaVersion"        -> scalaVersion.value,
        "target"              -> targetDir.absolutePath,
        "appJar"              -> appJar,
        "classDirectory"      -> classDir.absolutePath,
        "allDependencies"     -> updateReport.allFiles.map{_.absolutePath}.mkString(", "),
        "libraryDependencies" -> libDepFiles.mkString(", ")
      )
    }
  }
}
