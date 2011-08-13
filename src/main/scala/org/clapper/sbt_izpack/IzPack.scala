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

package org.clapper.sbt_izpack

import sbt._
import Keys._
import Defaults._
import Project.Initialize
import com.izforge.izpack.compiler.CompilerConfig

abstract class IzPackConfig extends IzPackConfigBase
{
    private var installDirectory: RichFile = new File(".")
    private var scalaVersion: String = ""

    IzPack.installDir apply { d => installDirectory = d }

    def InstallSourceDir: RichFile = installDirectory
}

trait IzPackConfigurator
{
    implicit def StringToRichFile(s: String): RichFile = new File(s)
    implicit def StringToFileSetGlob(s: String) = new FileSetGlob(s)

    def makeConfig(installSourceDir: RichFile, 
                   scalaVersion: String): IzPackConfig
}

/**
 * Plugin for SBT (Simple Build Tool) to configure and build an IzPack
 * installer.
 */
object IzPack extends Plugin
{
    // -----------------------------------------------------------------
    // Plugin Settings
    // -----------------------------------------------------------------

    val IzPack = config("izpack")
    //val izPackConfig = SettingKey[IzPackConfig]("izpack-config")
    val izPackConfig = SettingKey[Option[IzPackConfigurator]]("izpack-config")
    val installerJar = SettingKey[RichFile]("installer-jar")
    val createXML = TaskKey[RichFile]("create-xml", "Create IzPack XML")
    val createInstaller = TaskKey[Unit]("create-installer",
                                        "Create IzPack installer")
    val installDir = SettingKey[File]("install-source",
                                          "Directory containing auxiliary " +
                                          "source files.")

    val izPackSettings: Seq[sbt.Project.Setting[_]] = inConfig(IzPack)(Seq(

        installerJar <<= baseDirectory(_ / "target" / "installer.jar"),
        installDir <<= baseDirectory(_ / "src" / "installer"),

        // Use externalDependencyClasspath setting. 
        // See https://github.com/harrah/xsbt/wiki/Classpaths

        createXML <<= createXMLTask,
        createInstaller <<= createInstallerTask
    ))

    // -----------------------------------------------------------------
    // Methods
    // -----------------------------------------------------------------

    private def createXMLTask =
    {
        (izPackConfig, scalaVersion, installerJar, installDir, streams) map
        {
            (izPackConfig, sv, outputJar, installDir, streams) =>

            createXML(izPackConfig, installDir, sv, streams.log)
        }
    }

    private def createInstallerTask =
    {
        (izPackConfig, scalaVersion, installerJar, installDir, streams) map
        {
            (izPackConfig, sv, outputJar, installDir, streams) =>

            val configurator = izPackConfig.getOrElse(error("No IzPack config"))

            val log = streams.log

            val xml = createXML(izPackConfig, installDir, sv, log)

            log.info("Generating IzPack installer")
            izpackMakeInstaller(xml, outputJar)
        }
    }

    private def createXML(configuratorOpt: Option[IzPackConfigurator],
                          installDir: RichFile, 
                          scalaVersion: String,
                          log: Logger): RichFile =
    {
        val configurator = configuratorOpt.getOrElse(error("No IzPack config"))
        val izConfig = configurator.makeConfig(installDir, scalaVersion)

        log.info("Generating configuration XML")
        izConfig.generateXML(log)
        log.info("Created " + izConfig.installXMLPath)
        izConfig.installXMLPath
    }

    /**
     * Build the actual installer jar.
     *
     * @param installConfig   the IzPack installer configuration file
     * @param installerJar    where to store the installer jar file
     */
    private def izpackMakeInstaller(installConfig: RichFile, 
                                    installerJar: RichFile): Unit =
    {
        IO.withTemporaryDirectory
        {
            baseDir =>

            val compilerConfig = new CompilerConfig(
                installConfig.absolutePath,
                baseDir.getPath, // basedir
                CompilerConfig.STANDARD,
                installerJar.absolutePath
            )

            compilerConfig.executeCompiler
        }
    }
}
