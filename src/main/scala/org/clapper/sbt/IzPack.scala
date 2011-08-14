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

  * Neither the names "clapper.org", "Era", nor the names of its
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

package org.clapper.sbt

import sbt._
import Keys._
import Defaults._
import Project.Initialize
import com.izforge.izpack.compiler.CompilerConfig

/**
 * Plugin for SBT (Simple Build Tool) to configure and build an IzPack
 * installer.
 */
object IzPack extends Plugin
{
    // -----------------------------------------------------------------
    // Classes and Traits
    // -----------------------------------------------------------------

    abstract class IzPackConfig extends IzPackConfigBase

    trait IzPackConfigurator
    {
        implicit def StringToRichFile(s: String): RichFile = new File(s)
        implicit def StringToFileSetGlob(s: String) = new FileSetGlob(s)

        def makeConfig(installSourceDir: RichFile, 
                       scalaVersion: String): IzPackConfig
    }

    // -----------------------------------------------------------------
    // Plugin Settings and Tasks
    // -----------------------------------------------------------------

    val IzPack = config("izpack")
    //val izPackConfig = SettingKey[IzPackConfig]("izpack-config")
    val configGenerator = 
        SettingKey[Option[IzPackConfigurator]]("config-generator")
    val installerJar = SettingKey[RichFile]("installer-jar")
    val installDir = SettingKey[File]("install-source",
                                      "Directory containing auxiliary " +
                                      "source files.")

    val createXML = TaskKey[RichFile]("create-xml", "Create IzPack XML")
    val createInstaller = TaskKey[Unit]("create-installer",
                                        "Create IzPack installer")

    val izPackSettings: Seq[sbt.Project.Setting[_]] = inConfig(IzPack)(Seq(

        installerJar <<= baseDirectory(_ / "target" / "installer.jar"),
        installDir <<= baseDirectory(_ / "src" / "installer"),

        createXML <<= createXMLTask,
        createInstaller <<= createInstallerTask
    ))

    // -----------------------------------------------------------------
    // Methods
    // -----------------------------------------------------------------

    private def createXMLTask =
    {
        (configGenerator, scalaVersion, installerJar, installDir, streams) map
        {
            (oGenerator, sv, outputJar, installDir, streams) =>

            createXML(oGenerator, installDir, sv, streams.log)
        }
    }

    private def createInstallerTask =
    {
        (configGenerator, scalaVersion, installerJar, installDir, streams) map
        {
            (oGenerator, sv, outputJar, installDir, streams) =>

            val log = streams.log
            val xml = createXML(oGenerator, installDir, sv, log)
            makeInstaller(xml, outputJar, log)
        }
    }

    private def createXML(oGenerator: Option[IzPackConfigurator],
                          installDir: RichFile, 
                          scalaVersion: String,
                          log: Logger): RichFile =
    {
        oGenerator match
        {
            case Some(configurator) =>
                val izConfig = configurator.makeConfig(installDir, scalaVersion)

                log.info("Generating configuration XML")
                izConfig.generateXML(log)
                log.info("Created " + izConfig.installXMLPath.absolutePath)
                izConfig.installXMLPath

            case None =>
                error("No IzPack config")
        }
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
