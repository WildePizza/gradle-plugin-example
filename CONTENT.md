## The Tutorial

1. [Your First Gradle Plugin](tutorial/1-your-first-gradle-plugin.md) - implementing `Plugin<Project>`, registering a task, and declaring the plugin id.
2. [Your First Plugin Test](tutorial/2-your-first-plugin-test.md) - driving a real build with TestKit's `GradleRunner`.
3. [Declaring Tasks the Right Way](tutorial/3-declaring-tasks-the-right-way.md) - task types, lazy properties, and input/output annotations.
4. [Making Unit Testable Plugins](tutorial/4-making-unit-testable-plugins.md) - separating logic from Gradle so it can be tested in microseconds.
5. [Making Configurable Plugins](tutorial/5-making-configurable-plugins.md) - extensions, the project lifecycle, and why providers beat `afterEvaluate`.
6. [Reacting to Other Plugins](tutorial/6-reacting-to-other-plugins.md) - cooperating with `java` and hooking into lifecycle tasks without forcing anything on anyone.
7. [Wiring Tasks Together](tutorial/7-wiring-tasks-together.md) - letting providers carry task dependencies instead of maintaining `dependsOn` by hand.
8. [Publishing Your Plugin](tutorial/8-publishing-your-plugin.md) - marker artifacts, the Plugin Portal, and getting a release out without leaking credentials.

Hit an error? [TROUBLESHOOTING.md](TROUBLESHOOTING.md) collects the ones people run into most, and what they actually mean.

## Trying It Out

```bash
git clone https://github.com/intisy/gradle-plugin-example.git
cd gradle-plugin-example
./gradlew build
```

That compiles the plugin, generates its descriptor, validates the task types, and runs the full
test suite. Requires a JDK 17 or newer; Gradle itself comes from the wrapper.

To use the plugin from another build, install it locally:

```bash
./gradlew publishToMavenLocal
```

then, in the consuming project's `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

and in its `build.gradle`:

```groovy
plugins {
    id 'io.github.intisy.myplugin' version '1.0.0'
}

myplugin {
    fileContent = 'OMGWTFBBQ'
}
```

## What the Example Plugin Does

| Task | Type | What it does |
| --- | --- | --- |
| `dealwithit` | ad hoc | Prints a greeting from a `doLast` block |
| `mytask` | `MyTask` | Writes `build/myfile.txt` using the configured content |
| `myothertask` | `MyTask` | The same type reused for `build/otherfile.txt` |
| `mytestabletask` | `MyTestableTask` | Writes `build/testablefile.txt` through the Gradle-free `FileCreator` |
| `bundle` | `BundleTask` | Concatenates the other tasks' output. Declares no `dependsOn`; running it still runs its producers |
| `sourcereport` | `SourceReportTask` | Summarises the main source set. Registered **only** when the `java` plugin is applied, and hooked into `check` |

## Project Layout

```
src/main/java/io/github/intisy/
├── MyPlugin.java              the plugin entry point, registers everything
├── MyPluginExtension.java     the myplugin { } configuration block
├── MyTask.java                a configurable, incremental task type
├── MyTestableTask.java        the same, delegating to a testable implementation
├── SourceReportTask.java      registered only when the java plugin is present
├── BundleTask.java            consumes the other tasks' outputs, wired by provider
├── impl/FileCreator.java      the actual work, with no dependency on Gradle
├── impl/SourceReporter.java   likewise, and deterministic so up to date checks work
└── impl/Bundler.java          likewise

src/test/java/io/github/intisy/
├── TestMyPlugin.java          ProjectBuilder tests for registration and wiring
├── TestRealBuild.java         TestKit tests that run a real build
├── impl/TestFileCreator.java  plain unit tests, no Gradle involved
├── impl/TestSourceReporter.java
└── impl/TestBundler.java

testProjects/
├── simpleProject/             fixture applying the plugin with defaults
├── configuredProject/         fixture setting fileContent in a myplugin { } block
└── javaProject/               fixture applying java too, for the sourcereport task
```

## Using This as a Template

This repository is a GitHub template. After creating your own copy from it, rename everything to
your own coordinates in one step:

```bash
./bootstrap.sh --group com.acme.tools --id awesome --name awesome-gradle --strip-tutorial
```

That moves the package trees, rewrites the plugin id, the extension block name, the artifact
coordinates and the docs config, then runs `./gradlew build` to prove the result still works. Run it
with `--help` for the full set of options, and without `--strip-tutorial` if you want to keep the
tutorial alongside your own code. Windows users should run it from Git Bash or WSL.

## What is a Gradle Plugin?

A Gradle plugin is a library of code that, when loaded by a build script, adds new functionality to
the build. Gradle ships many built in ones; most users have met `java`:

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'junit:junit:4.13.2'
}
```

Applying `java` adds capabilities such as compiling Java sources, running unit tests, and packaging
a JAR. A custom plugin does the same kind of thing for your own conventions.

## Why Write One?

Rather than repeating custom logic in every `build.gradle`, you distribute it as a plugin:

- Organisations capture common practice (configuration, packaging, code standards) once and share it
  across many projects.
- Plugins are unit and integration tested independently, so build system changes can be made with
  some confidence rather than by hope.
- Plugins are versioned, letting each project control when it takes a build system change.
- Plugins can add genuinely new capability: custom packaging formats, new ways of running tests, and
  so on.

The result is that project build scripts shrink towards declaring dependencies and little else,
while still getting the full set of shared behaviour.

## Notes on This Fork

This repository began as a fork of
[jonathanhood/gradle-plugin-example](https://github.com/jonathanhood/gradle-plugin-example), whose
tutorial was written in Groovy against Gradle 2. The example plugin and all of the tutorials have
since been rewritten in Java against modern Gradle and extended with three further parts, covering lazy
properties, the configuration cache, and current TestKit. The shape of the lessons is still very
much the original author's.
