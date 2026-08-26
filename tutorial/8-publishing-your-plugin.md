# 8: Publishing Your Plugin

- [Home](../README.md)
- [Previous](7-wiring-tasks-together.md)

A plugin nobody can apply is not much use. This tutorial covers getting yours out of your own
checkout and into other people's builds.

We will cover:

- What actually gets published, and the marker artifact that makes `plugins { }` work.
- Publishing locally for testing.
- Publishing to the Gradle Plugin Portal.
- Publishing to Maven Central or GitHub Packages instead.
- Versioning, and automating releases.

## What Gets Published

Run `./gradlew publishToMavenLocal` on this project and look at what lands in `~/.m2/repository`:

```
io/github/intisy/gradle-plugin-example/1.0.0/gradle-plugin-example-1.0.0.jar
io/github/intisy/gradle-plugin-example/1.0.0/gradle-plugin-example-1.0.0.pom
io/github/intisy/gradle-plugin-example/1.0.0/gradle-plugin-example-1.0.0.module
io/github/intisy/myplugin/io.github.intisy.myplugin.gradle.plugin/1.0.0/io.github.intisy.myplugin.gradle.plugin-1.0.0.pom
```

The first three are the ordinary Java library: your classes, a POM, and Gradle Module Metadata. The
fourth is the interesting one, and it explains something that confuses nearly everyone.

### The marker artifact

When a build script says

```groovy
plugins {
    id 'io.github.intisy.myplugin' version '1.0.0'
}
```

Gradle has an *id*, not Maven coordinates. It cannot know your group and artifact name from the id
alone. So `java-gradle-plugin` publishes a tiny extra POM whose coordinates are derived mechanically
from the id: group `io.github.intisy.myplugin`, artifact `io.github.intisy.myplugin.gradle.plugin`.
That POM contains nothing but a redirect:

```xml
<groupId>io.github.intisy.myplugin</groupId>
<artifactId>io.github.intisy.myplugin.gradle.plugin</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
<dependencies>
  <dependency>
    <groupId>io.github.intisy</groupId>
    <artifactId>gradle-plugin-example</artifactId>
    <version>1.0.0</version>
  </dependency>
</dependencies>
```

Gradle resolves the id to that predictable location, reads the single dependency, and follows it to
the real jar. That is the whole mechanism.

Two practical consequences:

- **Both artifacts must reach the repository.** If only the jar is published, `plugins { }`
  resolution fails with "Plugin not found" while the jar sits right there. This is the usual cause
  of a plugin that works via `buildscript` but not via the plugins DSL.
- **The marker is generated for you.** You get it by applying `java-gradle-plugin` and declaring the
  plugin in the `gradlePlugin { }` block, as set up in [tutorial 1](1-your-first-gradle-plugin.md).

## Publishing Locally

For testing against a real consumer project, local Maven is enough:

```bash
./gradlew publishToMavenLocal
```

The consumer needs `mavenLocal()` in its **`settings.gradle`**, because plugins resolve before
`build.gradle` is read:

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

For iterating on a plugin while developing the build that uses it, a
[composite build](https://docs.gradle.org/current/userguide/composite_builds.html) is better still.
Add `includeBuild('../my-plugin')` to the consumer's `settings.gradle` and Gradle substitutes your
source directly, with no publish step at all.

## Publishing to the Gradle Plugin Portal

The portal is the default place consumers resolve plugins from. Add the publishing plugin:

```groovy
plugins {
    id 'java-gradle-plugin'
    id 'com.gradle.plugin-publish' version '1.3.1'
}
```

It reuses the `gradlePlugin { }` block you already have, plus the metadata the portal requires:

```groovy
gradlePlugin {
    website = 'https://github.com/intisy/gradle-plugin-example'
    vcsUrl = 'https://github.com/intisy/gradle-plugin-example'
    plugins {
        create('myplugin') {
            id = 'io.github.intisy.myplugin'
            implementationClass = 'io.github.intisy.MyPlugin'
            displayName = 'Gradle Plugin Example'
            description = 'A worked example of a Gradle plugin'
            tags = ['example', 'tutorial']
        }
    }
}
```

`website`, `vcsUrl`, `displayName`, `description` and `tags` are not decoration. The portal rejects
a submission that omits them, and they are what people see when searching.

Get an API key from your portal profile and put it somewhere that is **not** the repository:

```properties
# ~/.gradle/gradle.properties, never the project's
gradle.publish.key=...
gradle.publish.secret=...
```

In CI, pass them as environment variables instead:

```bash
GRADLE_PUBLISH_KEY=...  GRADLE_PUBLISH_SECRET=...  ./gradlew publishPlugins
```

Check everything is acceptable before committing to a version number:

```bash
./gradlew publishPlugins --validate-only
```

Publishing is permanent. A version can be deprecated but never replaced or deleted, so validate
first and get the version right.

> This repository does not apply `com.gradle.plugin-publish`. It is a tutorial rather than a
> published plugin, and keeping the build free of plugins that need credentials means
> `./gradlew build` works offline for everyone reading along.

## Publishing Somewhere Else

The portal is not the only option, and for an internal plugin it is the wrong one. `maven-publish`
is already applied here, so any Maven repository works:

```groovy
publishing {
    repositories {
        maven {
            name = 'GitHubPackages'
            url = 'https://maven.pkg.github.com/intisy/gradle-plugin-example'
            credentials {
                username = System.getenv('GITHUB_ACTOR')
                password = System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

Then `./gradlew publish`. Consumers add the same repository to `pluginManagement` and resolve as
normal. Note that both the jar and the marker go there, so the plugins DSL keeps working.

Maven Central additionally requires signed artifacts, javadoc and sources jars, and a verified
namespace. Add `signing`, plus:

```groovy
java {
    withSourcesJar()
    withJavadocJar()
}
```

## Versioning

The version comes from `gradle.properties` here:

```properties
artifact_version=1.0.0
```

A few conventions worth keeping:

- **Semantic versioning.** Consumers write `version '1.+'` more often than you would like. Breaking
  a build script's contract in a patch release is how you lose their trust.
- **A plugin's public API is bigger than its Java API.** Task names, the extension block name, and
  property names are all things users depend on. Renaming a task is a breaking change even though
  nothing about it is `public` in the Java sense.
- **Snapshots for iteration.** `1.1.0-SNAPSHOT` can be republished; a release cannot.

## Automating It

Releases should not depend on which laptop happened to run the command. This repository's CI is
three thin callers in `.github/workflows/` that delegate to reusable workflows in
[`intisy/workflows`](https://github.com/intisy/workflows):

```yaml
name: Tests

on:
  workflow_dispatch:
  push:
  pull_request:

jobs:
  tests:
    uses: intisy/workflows/.github/workflows/test.yml@main
    with:
      toolchains: gradle
      java_version: '17'
      gradle_test: build
```

The pattern matters more than the specific workflows: the repository holds no build logic of its
own, only a declaration of which shared workflow to run and with what inputs. A publish workflow
follows the same shape, triggered by a release being published and reading its credentials from
repository secrets.

Whatever you use, the rules are the same. Never commit credentials. Publish from a tag rather than a
branch, so a released version always corresponds to a known commit. Run the tests before publishing,
not after.

## Next Steps

That completes the tutorial. You can now write a plugin, structure its tasks so they are testable,
make it configurable, have it cooperate with other plugins, wire a task graph from real data flow,
and publish the result.

Where to go from here:

- The [Gradle User Manual](https://docs.gradle.org/current/userguide/userguide.html), particularly
  [developing custom plugins](https://docs.gradle.org/current/userguide/custom_plugins.html) and the
  [configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html).
- [Shared build services](https://docs.gradle.org/current/userguide/build_services.html) for state
  that outlives a single task, and the
  [Worker API](https://docs.gradle.org/current/userguide/worker_api.html) for expensive work you
  want parallelised.
- **You may not need a published plugin at all.** For build logic used only inside one repository,
  a convention plugin in `buildSrc` or an included build gives you the same structure, the same
  testability, and no publishing step. Reach for a published plugin when the logic genuinely has to
  cross repository boundaries.

If something goes wrong, [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) covers the errors that come up
most often.
