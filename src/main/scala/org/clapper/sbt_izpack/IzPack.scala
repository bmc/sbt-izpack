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

/**
 * Plugin for SBT (Simple Build Tool) to configure and build an IzPack
 * installer.
 */
object IzPack extends Plugin
{
    // -----------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------

    val IzPack = config("izpack")
    val installerConfig = SettingKey[Option[IzPackConfig]]("installer-config")
    val installerJar = SettingKey[RichFile]("installer-jar")
    val generate = TaskKey[Unit]("generate", "Generate IzPack installer")

    private def generateTask(izConfig: Option[IzPackConfig],
                             outputJar: RichFile,
                             streams: TaskStreams): Unit =
    {
        val cfg = izConfig.getOrElse(error("generate: No IzPack configuration"))
        generateInstaller(cfg, outputJar, streams)
    }

    val izPackSettings = inConfig(IzPack)(Seq(

        installerConfig := None,

        installerJar := Path(".") / "target" / "installer.jar",

        generate <<= (installerConfig, installerJar, streams) map generateTask
    ))

    override lazy val settings = super.settings ++ izPackSettings

    abstract class IzPackConfig(workingInstallDir: RichFile)
    extends IzPackConfigBase(workingInstallDir)

    // -----------------------------------------------------------------
    // Methods
    // -----------------------------------------------------------------

    /**
     * Build an installer jar, given a configuration object.
     *
     * @param config         the configuration object
     * @param installerJar   where to store the installer jar file
     */
    def generateInstaller(config: IzPackConfig, 
                          installerJar: RichFile,
                          streams: TaskStreams): Unit =
    {
        config.generate(streams)
        izpackMakeInstaller(config.installXMLPath, installerJar)
    }

    /**
     * Build the actual installer jar.
     *
     * @param installConfig   the IzPack installer configuration file
     * @param installerJar    where to store the installer jar file
     */
    def izpackMakeInstaller(installConfig: RichFile, 
                            installerJar: RichFile): Option[String] =
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
            None
        }
    }
}
