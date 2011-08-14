// -*- scala -*-
import org.clapper.sbt_izpack._
import java.io.File
import sbt._
import grizzled.file.util._

object IzPackConfiguration extends IzPackConfigurator
{
    def makeConfig(installSourceDir: RichFile, scalaVersion: String) =
    {
        new IzPackConfig
        {
            customXML =
            (
                <conditions>
                    <condition type="java" id="installonunix">
                        <java>
                            <class>com.izforge.izpack.util.OsVersion</class>
                            <field>IS_UNIX</field>
                        </java>
                    </condition>
                </conditions>
                <installerrequirements>
                    <installerrequirement condition="installonunix"
                                          message="Only installable on Unix"/>
                </installerrequirements>
            )

            new Info
            {
                appName = "foobar"
                appVersion = "1.0"
                author("Joe Schmoe", "schmoe@example.org")
                author("Brian Clapper", "bmc@clapper.org")
                url = "http://www.example.org/software/foobar/"
                javaVersion = "1.6"
                summaryLogFilePath = "$INSTALL_PATH/log.html"
            }

            // Use ISO3 language codes; that's what IzPack wants.

            languages = List("eng", "chn", "deu", "fra", "jpn", "spa", "rus")

            new Resources
            {
                new Resource
                {
                    id = "HTMLLicensePanel.license"
                    source = installSourceDir / "license.html"
                }

                new Resource
                {
                    id = "HTMLInfoPanel.info"
                    source = installSourceDir / "info.html"
                }

                new Resource
                {
                    id = "Installer.image"
                    source = installSourceDir / "logo.png"
                }

                new InstallDirectory
                {
                    """C:\Program Files\clapper.org\test""" on Windows
                    "/Applications/test" on MacOSX
                    "/usr/local" on Unix
                }
            }

            new Variables
            {
                variable("foo", "bar")
            }

            new Packaging
            {
                packager  = Packager.MultiVolume
                volumeSize = (1024 * 1024 * 1024)
            }

            new GuiPrefs
            {
                height = 768
                width  = 1024

                new LookAndFeel("metouia")
                {
                    onlyFor(Unix)
                }

                new LookAndFeel("liquid")
                {
                    onlyFor(Windows, MacOS)

                    params = Map("decorate.frames" -> "yes",
                                 "decorate.dialogs" -> "yes")
                }
            }

            new Panels
            {
                new Panel("HelloPanel")
                new Panel("HTMLInfoPanel")
                new Panel("HTMLLicencePanel")
                new Panel("TargetPanel")
                {
                    id = "target"
                    help = Map(
                        "eng" -> installSourceDir / "TargetPanelHelp_en.html",
                        "deu" -> installSourceDir / "TargetPanelHelp_de.html"
                    )
                }

                new Panel("PacksPanel")
                new Panel("InstallPanel")
                new Panel("ProcessPanel")
                new Panel("XInfoPanel")
                new Panel("FinishPanel")
                new Panel("DummyPanel")
                {
                    val scalaVersionDir = "scala-" + scalaVersion

                    jar = "project" / "boot" / scalaVersionDir / "lib" /
                          "scala-library.jar"

                    new Action("postvalidate", "PostValidationAction")
                    new Validator("org.clapper.izpack.TestValidator")
                }
            }

            new Packs
            {
                new Pack("Core")
                {
                    required = true
                    preselected = true

                    new SingleFile(installSourceDir / "license.html",
                                   "license.html")

                    new SingleFile(installSourceDir / "foo.sh",
                                   "$INSTALL_PATH/bin/foo.sh")
                    {
                        onlyFor(Unix, MacOS)
                    }

                    new SingleFile(installSourceDir / "foo.bat",
                                   "$INSTALL_PATH/bin/foo.bat")
                    {
                        onlyFor(Windows)
                    }

                    new FileSet("project" ** "*.jar", "$INSTALL_PATH")

                    new Executable("$INSTALL_PATH/bin/foo.bat")
                    {
                        onlyFor(Windows)
                    }

                    new Executable("$INSTALL_PATH/bin/foo.sh")
                    {
                        onlyFor(Unix, MacOS)
                    }

                    new Parsable("$INSTALL_PATH/bin/foo.bat")
                    {
                        onlyFor(Windows)
                    }

                    new Parsable("$INSTALL_PATH/bin/foo.sh")
                    {
                        onlyFor(Unix, MacOS)
                    }
                }

                new Pack("Source")
                {
                    required = false
                    preselected = true

                    new File("src" / "main" / "scala", "$INSTALL_DIR/src")
                }
            }
        }
    }
}

