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

case class Metadata(installSource: RichFile,
                    baseDirectory: RichFile,
                    scalaVersion: String,
                    private[sbt] val updateReport: UpdateReport)

/**
 * Plugin for SBT (Simple Build Tool) to configure and build an IzPack
 * installer.
 */
object IzPack extends Plugin
{
    // -----------------------------------------------------------------
    // Classes and Traits
    // -----------------------------------------------------------------

    // -----------------------------------------------------------------
    // Plugin Settings and Tasks
    // -----------------------------------------------------------------

    val IzPack = config("izpack")
    //val izPackConfig = SettingKey[IzPackConfig]("izpack-config")
    val configFile = SettingKey[File]("config-file")
    val installerJar = SettingKey[RichFile]("installer-jar")
    val installSource = SettingKey[File]("install-source",
                                         "Directory containing auxiliary " +
                                         "source files.")
    val installXML = SettingKey[File]("install-xml",
                                       "Path to the generated XML file.")

    val createXML = TaskKey[RichFile]("create-xml", "Create IzPack XML")
    val createInstaller = TaskKey[Unit]("create-installer",
                                        "Create IzPack installer")

    val clean = TaskKey[Unit]("clean", "Remove target files.")
    val showDeps = TaskKey[Unit]("show-deps")

    val izPackSettings: Seq[sbt.Project.Setting[_]] = inConfig(IzPack)(Seq(

        installerJar <<= baseDirectory(_ / "target" / "installer.jar"),
        installSource <<= baseDirectory(_ / "src" / "izpack"),
        installXML <<= baseDirectory(_ / "target" / "izpack.xml"),
        configFile <<= installSource(_ / "izpack.yml"),

        createXML <<= createXMLTask,
        createInstaller <<= createInstallerTask,
        showDeps <<= (managedClasspath in Runtime) map showDependencies
    ))
    inConfig(Compile)(Seq(
        // Hook our clean into the global one.
        clean in Global <<= (clean in IzPack).identity
    ))

    // -----------------------------------------------------------------
    // Methods
    // -----------------------------------------------------------------

    private def cleanTask: Initialize[Task[Unit]] =
    {
        (installXML, streams) map 
        {
            (installXML, streams) =>

            if (installXML.exists)
            {
                streams.log.debug("Deleting \"%s\"" format installXML)
                installXML.delete
            }
        }
    }

    private def showDependencies(classpath: Classpath): Unit =
    {
        for (cp <- classpath)
            println(cp)
    }

    private def createXMLTask =
    {
        (configFile, scalaVersion, baseDirectory, installSource, installXML,
         update, streams) map
        {
            (configFile, sv, baseDir, installSource, installXML, updateReport,
             streams) =>

            createXML(configFile, baseDir, installSource, installXML, sv,
                      updateReport, streams.log)
        }
    }

    private def createInstallerTask =
    {
        (configFile, scalaVersion, installerJar, baseDirectory, installSource,
         installXML, update, streams) map
        {
            (configFile, sv, outputJar, baseDirectory, installSource,
             installXML, updateReport, streams) =>

            val log = streams.log
            val xml = createXML(configFile, baseDirectory, installSource,
                                installXML, sv, updateReport, log)
            makeInstaller(xml, outputJar, log)
        }
    }

    private def createXML(configFile: File,
                          baseDirectory: RichFile,
                          installSource: RichFile,
                          installXML: RichFile,
                          scalaVersion: String,
                          updateReport: UpdateReport,
                          log: Logger): RichFile =
    {
        val sbtData = new SBTData(installSource, baseDirectory, scalaVersion,
                                  updateReport)
        val parser = new IzPackYamlConfigParser(sbtData, log)
        val izConfig = parser.parse(Source.fromFile(configFile))

        // Create the XML.

        val path = installXML.absolutePath
        log.info("Generating IzPack XML \"%s\"" format path)
        izConfig.generateXML(installXML.asFile, log)
        log.info("Created " + path)
        installXML
    }

    /**
     * Build the actual installer jar.
     *
     * @param izPackXML  the IzPack installer XML configuration
     * @param outputJar  where to store the installer jar file
     */
    private def makeInstaller(izPackXML: RichFile,
                              outputJar: RichFile,
                              log: Logger) =
    {
        IO.withTemporaryDirectory
        {
            baseDir =>

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
