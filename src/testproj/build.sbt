
name := "SBT IzPack Plugin Test"

version := "0.2"

organization := "org.clapper"

scalaVersion := "2.8.1"

libraryDependencies ++= Seq(
    "org.clapper" %% "sbt-izpack" % "0.4"
)

{
    val izPackXML = Path(".") / "target" / "install"
    val izPackConfig = new IzPackConfig(izPackXML) {
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
    }
    (installerConfig in IzPack) := Some(izPackConfig)
}
