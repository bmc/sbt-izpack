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
import scala.io.Source
import com.izforge.izpack.compiler.CompilerConfig

/**
 * Plugin for SBT (Simple Build Tool) to configure and build an IzPack
 * installer.
 */
object IzPackPlugin extends Plugin
{
    // -----------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------

    val IzPack = config("izpack") extend(Runtime)
    val configuration = SettingKey[Option[String]]("configuration")

/*
    val makeInstaller = TaskKey[Unit]("make-installer",
                                      "Generates an IzPack installer")
*/
    val izPackSettings: Seq[sbt.Project.Setting[_]] = inConfig(IzPack)(Seq(

        configuration := None
    ))

    override lazy val settings = super.settings ++ izPackSettings

    // -----------------------------------------------------------------
    // Methods
    // -----------------------------------------------------------------

    def generateInstallConfig(config: IzPackConfig): Option[String] =
    {
        config.generate
        None
    }

    /**
     * Build an installer jar, given a configuration object.
     *
     * @param config         the configuration object
     * @param installerJar   where to store the installer jar file
     */
    def izpackMakeInstaller(config: IzPackConfig, 
                            installerJar: RichFile): Option[String] =
    {
        config.generate
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
