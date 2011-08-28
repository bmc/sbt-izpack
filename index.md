---
title: "sbt-izpack: An IzPack plugin for SBT 0.10.x"
layout: withTOC
---

# Introduction

*sbt-izpack* is a plugin for the [Scala][]-based [SBT][] 0.10.x build tool.
[IzPack][] is an open source tool that allows you to create flexible
Java-based graphical and command-line installers. This plugin allows you to
use IzPack directly from your SBT 0.10.x project.

This document explains how to use the plugin.

# Differences from previous versions

This version of the plugin replaces an [SBT 0.7-based version][], and this
new version is a complete rewrite. The biggest difference between the two
versions of the plugin is the configuration syntax. The previous SBT 0.7
plugin used a Scala-based [DSL][] configuration syntax. This new version
uses an external [YAML][] configuration file, instead, and translates the YAML
file to the [IzPack XML][] installation file format.

The reasons for the switch to YAML include:

* The YAML configuration syntax more closely tracks the syntax of the
  official [IzPack XML][] installation files, making it simpler to
  understand how the plugin's configuration maps to the underlying XML
  configuration.
* The YAML configuration syntax is more compact; compared to the previous
  Scala DSL syntax, the YAML format make it easier to see the entire
  configuration at a glance.

However, switching from a previous configuration to this new one requires
rewriting your configuration.

[SBT 0.7-based version]: http://software.clapper.org/sbt-plugins/izpack.html

# Motivation

There are several reasons I wrote this plugin, but two reasons are paramount.

## Ease of invocation

There was no simple way to invoke the IzPack compiler from SBT. Rather than
craft a clumsy way to invoke IzPack via the command line, I wrote this
plugin so I could invoke it directly from within SBT.

## XML is a lousy configuration file format

IzPack uses a complex XML configuration file.

I dislike the whole trend of using XML as a configuration file format. Yes,
XML is ostensibly human-readable. But it isn't always human-*friendly*. I
agree with [Terence Parr][], author of [ANTLR][] and [StringTemplate][],
who wrote that [humans should not have to grok XML][grok-XML].

[Terence Parr]: http://www.cs.usfca.edu/~parrt/
[ANTLR]: http://www.antlr.org/
[StringTemplate]: http://stringtemplate.org/
[grok-xml]: http://www.ibm.com/developerworks/xml/library/x-sbxml.html

YAML is often a simpler, easier to read configuration syntax, and it can
model many of the same constructs as XML. Translating a YAML configuration
into the the IzPack XML syntax turns out to be straightforward.

# Getting the Plugin

First, within your SBT project, create `project/plugins/build.sbt` (if it
doesn't already exist) and add the following:

    libraryDependencies += "org.clapper" %% "sbt-izpack" % "0.1.2"

Next, in your main project `build.sbt` file, add:

    seq(org.clapper.sbt.izpack.IzPack.izPackSettings: _*)

Now the plugin is available to your SBT builds.

# Settings

The plugin provides the following new settings.

---

**`installSourceDir`**

---

The directory containing additional installer source files, such as the
HTML files for install screens.

Example:

    installSourceDir in IzPack <<= baseDirectory(_ / "src" / "install")
    
Default: `baseDirectory(_ / "src" / "izpack")`

---

**`configFile`**

---

The YAML file describing the installer configuration. (See the
section entitled [The YAML configuration file](#the_yaml_configuration_file)
for details.) Examples:

    sourceFile in IzPack <<= baseDirectory(_ / "src" / "install" / "install.yml")

Default: `installSourceDir(_ / "izpack.yml")`

---

**`installerJar`**

---

The path to the installer jar file IzPack is to generate.

Example:

    installerJar in IzPack <<= baseDirectory(_ / "target" / "install.jar")

Default: `baseDirectory(_ / "target" / "installer.jar")`

---

**`tempDirectory`**

---

Where the plugin should generate various installer-related temporary files.

Default: `baseDirectory(_ / "target" / "installtmp")`

---

**`variables`**

---

<a name="variables_setting"></a>

`variables` is a sequence of `(variableName, value)` pairs. For instance,
the following two lines define:

* a `${projectName}` variable that substitutes the name of the project, and
* a `${author}` variable

    name := "my-project"

    variables in IzPack <+= name {name => ("projectName", name)}

    variables in IzPack += ("author", "Brian Clapper")

These variables can be substituted within the YAML configuration file and
augment the [predefined variables](#predefined_variables) the plugin defines.

*sbt-izpack* automatically creates an IzPack XML `<variables>` section and
populates it with these variables and the predefined variables.

# Tasks

The plugin provides three new SBT tasks.

**`izpack:create-xml`**

<div class="indent" markdown="1">

Converts the YAML configuration file to the XML file required by IzPack.
You generally don't need to invoke this task yourself;
`izpack:create-installer` invokes it automatically. This task exists
primarily for debugging.

The tasks depends on `(packageBin in Compile)`, so your code will
automatically be compiled and packaged into its jar file(s) before the
installer XML is generated.

</div>

**`izpack:create-installer`**

<div class="indent" markdown="1">

Generates the installer jar file from your YAML configuration. The tasks
depends on `izpack:create-xml` which, in turn, depends on `(packageBin in
Compile)`, so your code will automatically be compiled and packaged into
its jar file(s) before the installer is created.

</div>

**`izpack:clean`**

<div class="indent" markdown="1">

Deletes all *sbt-izpack*-generated files and directories. `izpack:clean` is
also automatically linked into the main SBT `clean` task.

</div>

# The YAML configuration file

The YAML configuration file tracks the [IzPack XML][] file format fairly
closely. There are some differences, but the main difference is readability.

## Sample configuration file

You can find a [sample configuration file here](install-yml.txt).

## Variables

Like "regular" IzPack, the *sbt-izpack* plugin supports variable
substitution within its configuration file. *sbt-izpack* provides some
[predefined variables](#predefined_variables), and you can add your own in
your build file.

Inside a source file to be edited, variable references are of the form
`${varname}`, as in the Unix shell. A shortened `$varname` is also support.
The `${}` long form also supports a default syntax: `${varname?default}`.
If the reference variable has no value, then the default value is supplied,
instead. (The `?default` syntax is not supported for the short form
reference.)

### Defining variables

Recall, from the previous discussion of the
[`variables` setting](#variables_setting), that you can set your own
variables by including logic like the following, in your `build.sbt` file:

    variables in IzPack <+= name {name => ("projectName", name)}

    variables in IzPack += ("author", "Brian Clapper")

With those definitions in place, any reference to `${projectName}` within
the configuration YAML is replaced with "my-project", and any reference to
`${author}` is replaced with "Brian Clapper".

You can define any number of variables. If the plugin logic encounters a
variable that isn't defined, it simply replaces the variable reference with
an empty string (like *bash* does).

### Predefined variables

In addition to the variables you define in your build file, *sbt-izpack*
also defines the following variables:

* `${allDependencies}`: The names of all the jars SBT needs to build your
  project, as a single string of paths separated by commas. This string
  can be substituted directly into the `includes` value of a `fileset`.
  (See [Fileset](#fileset), below.)
* `${appName}`: The value of the SBT `name` (project name) setting.
* `${appVersion}`: The value of the SBT `version` (project version) setting.
* `${appJar}`: The compiled and packaged jar file for your project.
* `${baseDirectory}`: The value of the SBT `baseDirectory` setting.
* `${classDirectory}`: The directory where compiled classes go.
* `${installSourceDir}`: The value of the `installSourceDir` setting. (See
  above.)
* `${libraryDependencies}`: The value of the SBT `libraryDependencies` setting.
* `${normalizedName}`: The SBT-normalized name of your application. SBT uses
  this name to generate jar file names and the like.
* `${scalaVersion}`: The Scala version used to compile your project.

## Types

*sbt-izpack* uses [SnakeYAML](http://www.snakeyaml.org/) and supports
the standard YAML types. In particular:

* Where a field is *boolean*, you can specify any legal
  [YAML boolean](http://yaml.org/type/bool.html) string, namely, "y", "n",
  "yes", "no", "true", "false", "on" or "off", without regard to upper- or
  lower-case.
* Where a field is *integer*, its value must be a legal
  [YAML int](http://yaml.org/type/int.html).

The YAML configuration file is broken into sections, just like its IzPack
XML counterpart.

## Unsupported sections

*sbt-izpack* does not directly support some of the section in the underlying
IzPack XML, including:

* `<variables>`. The *sbt-izpack* variable substitution mechanism is
  easier to use and generates an IzPack XML `<variables>` section.
* `<dynamicvariables>`. If you want to define dynamic variables, use a
  [`customXML`][Custom XML] subsection.
* `<conditions>`. If you want to define your own conditions in the IzPack
  XML, use a [`customXML`][Custom XML] subsection.
* `<refpack>`. If you want to define your own refpacks in the IzPack
  XML, use a [`customXML`][Custom XML] subsection.
* `<native>`.  If you want to specify this element, use a
  [`customXML`][Custom XML] subsection.

## Common sections and settings

Many sections support common subsections and settings, described here.

### Paths

When a path is required, use a Unix-style forward-slash ("/") as the path
separator; *sbt-izpack* will convert it to a backslash on Windows. Also,
the [predefined variables](#predefined_variables) can be a big help:

    resource:
      src: $baseDirectory/src/resources/license.txt

### Custom XML

While the *sbt-izpack* contains everything you'll need for most installers,
it's possible that *sbt-izpack* will not support a certain obscure piece of
IzPack XML you require. Most sections support a `customXML` key, allowing
you to insert your own XML at the end of the section. For example:

    customXML:
      - |
        <conditions>
          <condition type="java" id="installonunix">
            <java>
              <class>com.izforge.izpack.util.OsVersion</class>
              <field>IS_UNIX</field>
            </java>
          </condition>
        </conditions>

When a `customXML` key is supplied, its value is a list of
[YAML block literals][], which each literal representing a single XML
element to be inserted into the generated IzPack XML.

[YAML block literals]: http://en.wikipedia.org/wiki/YAML#Block_literals

Those sections that support `customXML` are clearly indicated, below.

### The "condition" setting

When a section supports a `condition` setting, the condition value consists
of one or more IzPack conditions, separated by a vertical bar ("|") symbol.
A condition is either an [IzPack built-in condition][] or one defined in a
[`customXML`][Custom XML] subsection.

Example:

    condition: izpack.windowsinstall.vista|izpack.windowsinstall.7

### The "os" subsection

Some sections support an `os` setting, which corresponds to an IzPack XML
`<os>` element. The `os` setting tells IzPack that the parent section only
applies to the specified the operating system family or families.

In *sbt-izpack*'s YAML configuration, the `os` setting is a comma-separated
list of operating system family names. Supported names are: `macosx`,
`unix`, and `windows`. (`unix` includes Linux, FreeBSD, Solaris, and other
Unix variants.)

Examples:

    os: macosx, unix
    os: windows

## Configuration file sections

### The "installation" section

Unlike the XML IzPack configuration, the YAML configuration format does not
have a root-level `installation` section.

### The "info" section

The `info` section corresponds to the IzPack [`info`][izpack-info] XML
element and supports the following settings and subsections:

[izpack-info]: http://izpack.org/documentation/installation-files.html#the-information-element-info

#### "info" settings

**`appName`** (string): The application name. **Required.**

**`appVersion`** (string): The application version. **Required.**

** `appSubPath`** (string): The subpath for the default installation path.
*Optional*. Default: the `appName`

**`url`** (string): The URL of the web site for the application.
*Optional.* No default.

**`javaVersion`** (string): The minimum Java runtime version required to
run the application being installed. Values can be `1.2`, `1.2.2`, `1.4`,
etc. *Optional.* Default: `1.6`

**`requiresJDK`** (boolean): Whether a JDK (as opposed to just a JRE) is
required to run the installed application. *Optional.* Default: `no`

**`webdir`** (string): Causes a web installer to be created and specifies a
URL from which to retrieve packages at run-time. *Optional.* No default.

**`summaryLogFilePath`** (string): A path for an installer log file.
*Optional.* No default.

**`writeInstallationInfo`** (boolean): Whether or not a
`.installinformation` file should be written when the installer is run; the
file includes information about installed packs. *Optional.* Default: `yes`

**`pack200`** (boolean): If enabled, this item causes every unsigned JAR
file you add to your packs to be compressed using Pack200. *Optional*.
Default: `no`.

**`createUninstaller`** (boolean): Specifies whether or not to create an
uninstaller. *Optional*. Default: `no`.

#### "info" subsections

##### `author`

<div class="indent" markdown="1">

A section consisting of two subelements, specifying the author
or authors of the application. This section can be specified multiple
times. The subelements are:

**`name`**: The author name. **Required**.

**`email`**: The author's email address. *Optional*. No default.

</div>

##### `runPrivileged`

<div class="indent" markdown="1">

This is a special, optional section containing three subelements. Enabling
this capability causes the installer to try to run with administrator
privileges.

**`enabled`** (boolean): Whether or not the section is enabled. Allows you
to easily disable it, without commenting the whole section out. *Optional*.
Default: `yes`

**`uninstaller`** (boolean): Whether or not to *disable* the privilege
   escalation for the uninstaller. *Optional*. Default: `false`.

**`condition`** (string): The conditions under which the privilege escalation
applies. Useful for restricting it to, say, Windows. *Optional*. No default.

</div>

##### `customXML`

The `info` section supports a [`customXML`][Custom XML] subsection.

#### Example "info" section

    info:
      appName: Yowza
      appVersion: 1.0
      url: https://github.com/bmc/yowza
      summaryLogFilePath: "/tmp/out"
      javaVersion: 1.5
      author:
        name: Brian Clapper
        email: bmc@clapper.org
      author:
        name: Joe Schmoe
      createUninstaller: no
      runPrivileged:
        enabled: no
        uninstaller: yes
        condition: izpack.windowsinstall.vista|izpack.windowsinstall.7
      pack200: yes

### The "languages" section

The `languages` section corresponds to the IzPack [`locale`][izpack-locale]
XML element, though it dispenses with the `locale` parent in factor of a
simple list of ISO3 language codes. For example:

    languages:
      - eng
      - deu
      - fra

[izpack-locale]: http://izpack.org/documentation/installation-files.html#the-localization-element-locale

### The "packaging" section

The `packaging` section corresponds to the IzPack
[`packaging`][izpack-packaging] XML element and supports the following
settings.

#### "packaging" settings

**`packager`** (string): The IzPack packager type. *Optional.* Legal values:
`single-volume`, `multi-volume`. Default: `single-volume`.

**`volumesize`** (integer): The size of the volumes. *Optional.* No default.
Only supported if `packager` is `multi-volume`.

**`firstVolumeFreeSpace`** (integer): Free space on the first volume, to be
used for the installer jar and additional resources. *Optional*. No
default. Only supported if `packager` is `multi-volume`.

Unlike the IzPack XML format, the plugin's YAML configuration does not
support an `unpacker` setting, since its value can be inferred from the
packager type.

See the IzPack documentation for complete details on the difference between
single-volume and multi-volume installers.

[izpack-packaging]: http://izpack.org/documentation/installation-files.html#the-packaging-element-packaging

#### Example "packaging" section

    packaging:
      packager: single-volume

### The "installerRequirement" section

The `installerRequirement` section corresponds to the IzPack
[`installerrequirement`][izpack-installerrequirements] XML element. Unlike
the IzPack XML format, there's no outer `installerrequirements` element;
instead, just include multiple `installerRequirement` YAML sections.

It supports the following settings:

**`condition`** (string): A string indicating a requirement condition. See
[the "condition" setting][] for more information.

**`message`** (string): Text or a language key defining the message to be
shown, before the installer exits, in case of a missing requirement.

[izpack-installerrequirements]: http://izpack.org/documentation/installation-files.html#the-installer-requirements-element-installerrequirements

#### Example "installerRequirement" section

    installerRequirement:
      condition: izpack.windowsinstall.vista|izpack.windowsinstall.7
      message: Installer can only be run on Windows

### The "guiprefs" section

The `guiprefs` section corresponds to the IzPack
[`guiprefs`][izpack-guiprefs] XML element and supports the following
settings and subsections.

[izpack-guiprefs]: http://izpack.org/documentation/installation-files.html#the-gui-preferences-element-guiprefs

#### "guiprefs" Settings

**`resizable`** (boolean): Whether or not the installer window can be resized.
*Optional*. Default: `yes`

**`width`** (integer): The width, in pixels, of the installer window.
*Optional*. Default: 800

**`height`** (integer): The height, in pixels, of the installer window.
*Optional*. Default: 600

#### "guiprefs" subsections

##### `laf`

<div class="indent" markdown="1">

A look-and-feel descriptor, consisting of the following settings:

**`name`** (string): The name of a look and feel.

**`os`** (string): The operating system families to which the look-and-feel
entry applies. See [The "os" setting][] for details.

All other settings are parameters specific to the look and feel. See the
[IzPack documentation on `guiprefs`][izpack-guiprefs] for details.

</div>

##### `customXML`

<div class="indent" markdown="1">

`guiprefs` also supports a [`customXML`][Custom XML] subsection.

</div>

#### Example "guiprefs" section

    guiprefs:
      resizable: no
      laf:
        name: looks
        os: windows
        variant: extwin

### The "resources" section

The `resources` section corresponds to the IzPack
[`resources`][izpack-resources] XML element and supports the following
subsections.

#### "resources" subsections

##### `resource`

<div class="indent" markdown="1">

A single resource. The following settings are supported:

**`id`** (string): The resource ID. Note that for IzPack resources, like
the Info panel and the License panel, these IDs have specific names, and
spelling counts. For instance:
    
* `HTMLInfoPanel.info`: The ID for an HTML version of the Info panel
* `HTMLLicencePanel.licence`: The ID for an HTML version of the License
   panel. Note the British spelling of "licence".

See the [IzPack documentation][izpack-resources] for details.

**`src`** (string): The path to the source file containing the contents
of the resource. **Required**.

**`parse`** (boolean): Whether or not the IzPack compiler should parse the
file and do IzPack variable substitution. Note that *sbt-izpack* currently
does *not* parse the file, so the only variable substitution supported is
the native IzPack substitution. (That may change in a future release of
*sbt-izpack*, however.) *Optional*. Default: `no`

**`type`** (string): Only examined if `parse` is `yes`, this value defines
what kind of parsing IzPack is to do. *Optional*. Legal values: `ant`,
`at`, `java`, `javaprop`, `plain`, `shell`, `xml`. Default: `plain`.

**`encoding`** (string): Specifies the resource encoding, if necessary. Only
useful for a text resource. *Optional*. No default.

</div>

##### `installDirectory`

<div class="indent" markdown="1">

Specifies a default installation directory. This configuration subsection
is provides convenient shorthand for the IzPack "TargetPanel.dir.*os*"
resource. It has the following settings:

**`os`** (string): The operating system to which the installation directory
applies. See [The "os" setting][] for details.

**`path`** (string): The path to the directory (on the system where the
installer runs).

</div>

##### `customXML`

<div class="indent" markdown="1">

`resources` also supports a [`customXML`][Custom XML] subsection.

</div>

[izpack-resources]: http://izpack.org/documentation/installation-files.html#the-resources-element-resources

#### Example "resources" section

    resources:
      resource:
        id: HTMLInfoPanel.info
        src: $installSourceDir/info.html
        parse: no
      resource:
        id: HTMLLicencePanel.licence
        src: $installSourceDir/license.html
        parse: no
      resource:
        id: XInfoPanel.info
        src: $installSourceDir/final-screen.txt
        parse: yes
        parseType: xml
      resource:
        id: Installer.image
        src: $installSourceDir/logo.png
      installDirectory:
        os: unix
        path: /usr/local/supertool
      installDirectory:
        os: windows
        path: C:/Program Files/SuperTool
      installDirectory:
        os: macosx
        path: /usr/local/supertool

### The "panels" section

The `panels` section corresponds to the IzPack [`panels`][izpack-panels]
XML element and currently supports one or more `panel` subsections and an
optional [`customXML`][Custom XML] subsection.

[izpack-panels]: http://izpack.org/documentation/installation-files.html#the-panels-element-panels

Each `panel` subsection supports the following subsections and settings:

#### "panel" settings

**`classname`** (string): The class name of the panel, which can be an
IzPack built-in class or a custom class. **Required**.

**`id`** (string): An identifier for the panel. *Optional*. No default.

**`condition`** (string): A string indicating a condition for the panel.
See [the "condition" setting][] and the documentation for
[IzPack `panels`][izpack-panels] for more information. *Optional*. No
default.

**`jar`** (string): The path to the jar file, for a custom panel.
*Optional*. No default.

#### "panel" subsections

##### `help`

<div class="indent" markdown="1">

Specifies the contents of a help screen to be shown if the user presses the
help button on the panel. *Optional*. Multiple `help` settings are
permitted, to allow for separate languages. `help` supports the following
settings:
  
**`iso3`** (string): The ISO3 language code for the help screen.

**`src`** (string): The path to the source file for the screen.

</div>

##### `validator`

<div class="indent" markdown="1">

A value validator for the panel. *Optional*. Multiple `validator` sections
are permitted. `validator` supports the following settings:
  
**`classname`** (string): The fully-qualified name of a class that implements
`com.izforge.izpack.installer.DataValidator`.

</div>

##### `action`

<div class="indent" markdown="1">

Optional actions for the panel. Multiple `action` sections
are permitted. Each takes the following settings:

**`stage`**: The stage at which the action should be triggered. *Required*.
Legal values: `preconstruct`, `preactivate`, `prevalidate`, or
`postvalidate`.

**`classname`** (string): The fully-qualified name of a class that implements
`com.izforge.izpack.installer.PanelAction`.

</div>

### Example "panels" section

    panels:
      panel:
        className: HelloPanel
        help:
           iso3: eng
           src: $installSource/HellPanelHelp_eng.html
        help:
           iso3: deu
           src: $installSource/HellPanelHelp_deu.html
      panel:
        className: LicencePanel
      panel:
        className: TargetPanel
      panel:
        className: InstallPanel
      panel:
        className: UserInputPanel
        id: myuserinput
        condition: pack2selected
        action:
          stage: preconstruct
          classname: ConnectionPreConstructAction
          stage: preactivate
          classname: ConnectionPreActivateAction
      panel:
        className: FinishPanel
        jar: MyFinishPanel.jar

### The "packs" section

The `packs` section corresponds to the IzPack
[`packs`][izpack-packs] XML element and currently supports one or more
`pack` subsections. Each `pack` subsection supports the following
subsections and settings:

[izpack-packs]: http://izpack.org/documentation/installation-files.html#the-packs-element-packs

#### Settings

**`name`** (string): The pack name, is it will be displayed on the screen.
**Required**.

**`description`** (string): The description of the pack, displayed to the
user. *Optional*. No default.

**`required`** (boolean): Whether or not the panel is required. *Optional*.
Default: `no`.

**`os`** (string): Allows you to target the pack to a specific operating system.
See [The "os" setting][] for details. *Optional*. No default.

**`preselected`** (boolean): Determines whether the pack is preselected in the
dialog or not. *Optional*. Default: `no`

**`loose`** (boolean): If `yes`, indicates that the files are not located
in the installer jar. See the [IzPack documentation][izpack-packs] for
details on this setting. *Optional*. Default: false

**`hidden`** (boolean): Whether or not the pack will be shown on the Packs
installer panel. The bytes of each hidden pack are used to calculate the
required space, but the pack itself won't be shown. A hidden pack can be
selected conditionally, so you *must* specify a condition to control its
installation. See the [IzPack documentation][izpack-packs] for further
details. *Optional*. Default: `no`.

**`id`** (string): A unique ID for the pack, to be used for
internationalization. *Optional*. Default: None. See the
[Notes](#pack-notes), below.

**`packImgId`** (string): A reference a unique resource that represents the
pack's image for the `ImgPacksPanel`. The resource should be defined in the
`resources` section, as a `resource` using the same value for its `id`
setting.

**`condition`** (string): A string indicating a condition for the panel.
See [the "condition" setting][] and the documentation for
[IzPack `panels`][izpack-panels] for more information. *Optional*. No
default.

**`depends`** (string): The name of another pack that this pack depends on.
This pack can only be selected for installation if its dependencies are
also selected. The `depends` element can be specified multiple times, to
depend on more than one pack. *Optional*. No default.
  
### Subsections

`packs` supports the following subsections.

##### `file`

<div class="indent" markdown="1">

Specifies a file or directory to be installed as part of the pack.
*Optional*. No default. `file` supports the following settings:
  
**`src`** (string): The path to the source file or directory to be included
in the pack. **Required**.

**`targetDir`** (string): The target directory to which to install the
file. **Required**.

**`os`** (string): Allows you to target the file to a specific operating system.
See [The "os" setting][] for details. *Optional*. No default.

**`unpack`** (boolean): If `yes` and if the file is an archive, then its
contents will be unpacked during installation. *Optional*. Default: `no`.

**`override`** (string): How to handle files that already exist.
*Optional*. Legal values: `true`, `false`, `asktrue`, `askfalse`, `update`.
Default: `update`. See the documentation for the
[IzPack `file` element][izpack-file] for a complete description of these
values. (Usually, the default suffices.)

**`condition`** (string): The ID of a condition that must be fulfilled for
the file to be installed. See [the "condition" setting][] for details.
*Optional*. No default.

[izpack-file]: http://izpack.org/documentation/installation-files.html#file-add-files-or-directories

</div>

##### `singleFile`

<div class="indent" markdown="1">

Specifies a single file (not a directory) to be installed as part of the
pack. *Optional*. No default. `singleFile` supports the following settings:

**`src`** (string): The path to the source file or directory to be included
in the pack. **Required**.

**`targetFile`** (string): The target file name (i.e., where to install the
file). **Required**.

**`os`** (string): Allows you to target the file to a specific operating system.
See [The "os" setting][] for details. *Optional*. No default.

**`override`** (string): How to handle files that already exist.
*Optional*. Legal values: `true`, `false`, `asktrue`, `askfalse`, `update`.
Default: `update`. See the documentation for the
[IzPack `file` element][izpack-file] for a complete description of these
values. (Usually, the default suffices.)

**`condition`** (string): The ID of a condition that must be fulfilled for
the file to be installed. See [the "condition" setting][] for details.
*Optional*. No default.

[izpack-singlefile]: http://izpack.org/documentation/installation-files.html#singlefile-add-a-single-file

</div>

##### `fileset`

<div class="indent" markdown="1">

Allows files to be specified via wildcards. *Optional*. No default. The
syntax for *sbt-izpack*'s `fileset` differs slightly from the one supported
in the IzPack XML. `fileset` supports the following settings.

**`includes`** (string): A comma-separated list of files or patterns to be
included in the pack. See [Fileset patterns](#fileset_patterns), below, for
details on the supported patterns. May be specified more than once. At
least one `includes` setting is **required**.

**`excludes`** (string): A comma-separated list of files or patterns to be
excluded from the list of files matched by `includes`. See
[Fileset patterns](#fileset_patterns), below, for details on the supported
patterns. *Optional*. No default.

**`regexExcludes`** (string): A regular expression; any file or path
matching the regular expression is excluded from list of files matched by
`includes`. *Optional*. No default.

**`targetDir`** (string): The target directory to which to install the
matching files. **Required**.

**`os`** (string): Allows you to target the file to a specific operating system.
See [The "os" setting][] for details. *Optional*. No default.

**`unpack`** (boolean): If `yes` and if the file is an archive, then its
contents will be unpacked during installation. *Optional*. Default: `no`.

**`override`** (string): How to handle files that already exist.
*Optional*. Legal values: `true`, `false`, `asktrue`, `askfalse`, `update`.
Default: `update`. See the documentation for the
[IzPack `file` element][izpack-file] for a complete description of these
values. (Usually, the default suffices.)

**`condition`** (string): The ID of a condition that must be fulfilled for
the file to be installed. See [the "condition" setting][] for details.
*Optional*. No default.

**`caseSensitive`** (boolean): Indicates whether file names are
case-sensitive or not. *Optional*. Default: `false`

</div>

##### `parsable`

<div class="indent" markdown="1">

Any files specified in this section are parsed *after* installation and
variables are replaced within them. *Optional*. No default. `parsable`
supports the following settings:
  
**`targetFile`** (string): The installed file to be parsed. **Required**.

**`type`** (string): Defines what kind of parsing IzPack is to do.
*Optional*. Legal values: `ant`, `at`, `java`, `javaprop`, `plain`,
`shell`, `xml`. Default: `plain`.

**`encoding`** (string): The file encoding. *Optional*. No default.

**`os`** (string): Allows you to target the file to a specific operating system.
See [The "os" setting][] for details. *Optional*. No default.

**`condition`** (string): The ID of a condition that must be fulfilled for
the file to be parsed. See [the "condition" setting][] for details.
*Optional*. No default.

</div>

##### `executable`

<div class="indent" markdown="1">

Useful if you need to execute something during the installation process. It
can also be used to set the executable flag on Unix-like systems.
*Optional*. No default. `executable` supports the following settings:

**`targetFile`** (string): The installed file to be run or marked
executable. **Required**.

**`class`** (string): If the executable is a jar file, this is the Java
class to run. *Optional*. No default.

**`executableType`** (string) : `bin` or `jar`. *Optional*. Default: `bin`

**`stage`** (string): When to launch. *Optional*. Default: `never`. Legal
values:

* `postinstall`: Launch immediately after installation completes
* `never`: Never launch it. Useful to set the +x flag on Unix.
* `uninstall`: Launch when the application is uninstalled, before any
   files are deleted.

**`os`** (string): Only run if installing on the specified operating
system(s). See [The "os" setting][] for details. *Optional*. No default.

**`keep`** (boolean): Whether the file will be kept after execution.
*Optional*. Default: `yes`

**`condition`** (string): The ID of a condition that must be fulfilled for
the file to be executed. See [the "condition" setting][] for details.
*Optional*. No default.

**`arg`**: (string): A command line argument to pass to the executable.
Multiple `arg` settings are permitted. Arguments are passed in the order
they appear in the configuration file. *Optional*. No default.

</div>

##### `updateCheck`

<div class="indent" markdown="1">

Updates an already-installed package, removing superfluous files after
installation. It supports two settings:

**`includes`** (string): A comma-separated list of files or patterns to be
included in the pack. See [Fileset patterns](#fileset_patterns), below, for
details on the supported patterns. May be specified more than once. At
least one `includes` setting is **required**.

**`excludes`** (string): A comma-separated list of files or patterns to be
excluded from the list of files matched by `includes`. See
[Fileset patterns](#fileset_patterns), below, for details on the supported
patterns. *Optional*. No default.

</div>

### Fileset patterns

The patterns supported by the `fileset` subsection's `includes` and
`excludes` settings are extended glob patterns. The usual Unix-like glob
patterns apply, but, in addition, "\*\*" can be used to indicate "any
subdirectory or subtree". For instance:

* `includes: lib`: include the lib directory and its contents
* `includes: **/*.jar`: include all jar files found underneath this directory
* `includes: $baseDirectory/target/**/*.class, $baseDirectory/**/*.scala`:
  include all class files underneath the target directory and all Scala
  source files underneath the top-level directory.

**Note**: Unlike IzPack itself, *sbt-izpack* doesn't have a set of patterns
it automatically ignores. Thus, it *will* descend into Subversion `.svn`
subdirectories, for instance. Use `regexExcludes` to exclude those directories.

[izpack-fileset]: http://izpack.org/documentation/installation-files.html#fileset-add-a-fileset

### Example "packs" section

    packs:
      pack:
        name: Core
        required: true
        preselected: true
        description: The binaries and libraries
        singleFile:
          src: $installSourceDir/foo.sh
          targetFile: $INSTALL_PATH/bin/foo.sh
          os: unix
        parsable:
          targetFile: $INSTALL_PATH/bin/foo.sh
          os: unix
        executable:
          targetFile: $INSTALL_PATH/bin/foo.sh
          os: unix
        singleFile:
          src: $installSourceDir/foo.bat
          targetFile: $INSTALL_PATH/bin/foo.bat
          os: windows
        parsable:
          targetFile: $INSTALL_PATH/bin/foo.bat
          os: windows
        fileset:
          includes: ${libraryDependencies}
          regexExcludes: posterous
          targetDir: $INSTALL_PATH/lib


### Notes {#pack-notes}

1. *sbt-izpack* does not directly support the IzPack `<refpackset>` element
   necessary for internationalization. (This may change, in a future
   release of *sbt-izpack*.) However, with a [`customXML`][Custom XML]
   subsection, you can supply your own `<refpackset>` elements.

2. *sbt-izpack* does not support the `<additionaldata>` pack element. If you
   need to supply this element, use a [`customXML`][Custom XML] subsection.

#### Subsections

* `packs` also supports a [`customXML`][Custom XML] subsection.

# Change log

The change log for all releases is [here][changelog].

# Author

Brian M. Clapper, [bmc@clapper.org][]

# Copyright and License

This software is copyright &copy; 2010-2011 Brian M. Clapper and is
released under a [BSD License][].

# Patches

I gladly accept patches from their original authors. Feel free to email
patches to me or to fork the [GitHub repository][] and send me a pull
request. Along with any patch you send:

* Please state that the patch is your original work.
* Please indicate that you license the work to the Grizzled-Scala project
  under a [BSD License][].

[BSD License]: license.html
[Scala]: http://www.scala-lang.org/
[GitHub repository]: http://github.com/bmc/sbt-izpack
[GitHub]: https://github.com/bmc/
[Scala Tools Maven repository]: http://www.scala-tools.org/repo-releases/
[Scala Maven Guide]: http://www.scala-lang.org/node/345
[SBT]: https://github.com/harrah/xsbt/
[bmc@clapper.org]: mailto:bmc@clapper.org
[changelog]: CHANGELOG.html
[SBT cross-building]: http://code.google.com/p/simple-build-tool/wiki/CrossBuild
[IzPack XML]: http://izpack.org/documentation/installation-files.html
[Izpack]: http://izpack.org/
[IzPack built-in condition]: http://izpack.org/documentation/installation-files.html#built-in-conditions
[DSL]: http://en.wikipedia.org/wiki/Domain-specific_language
[YAML]: http://yaml.org/
[Custom XML]: #custom_xml
[the "condition" setting]: #the_condition_setting
[the "os" setting]: #the_os_setting
