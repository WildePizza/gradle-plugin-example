# 1: Your First Gradle Plugin

- [Home](../README.md)
- [Next](2-your-first-plugin-test.md)

Gradle plugins start simple. You write a class that adds tasks to a project, tell Gradle where to
find it, and apply it from a build script. In this tutorial we will cover:

- How to create a simple plugin.
- How to add a task to the project.
- How to tell Gradle about your plugin.
- How to use your plugin in a project.

## Basic Plugin Structure

At its core a plugin is just a JAR file with some classes and a properties file that names the
entry point. The contents of a simple plugin JAR look something like:

```
.
├── io
│   └── github
│       └── intisy
│           └── MyPlugin.class
└── META-INF
    ├── gradle-plugins
    │   └── io.github.intisy.myplugin.properties
    └── MANIFEST.MF
```

The properties file under `META-INF/gradle-plugins` is what maps a plugin id to an implementation
class. Its name is the plugin id, so the file above declares the id `io.github.intisy.myplugin`.
You will see in a moment that you do not have to write that file yourself.

## Defining a Plugin Implementation

The plugin code itself is small. Implement `Plugin<Project>` and write an `apply` method.

The following example adds a `dealwithit` task to whatever project it is applied to.

```java
package io.github.intisy;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("dealwithit", task ->
                task.doLast(action -> System.out.println("(•_•) ( •_•)>⌐■-■ (⌐■_■)")));
    }
}
```

Two details in that snippet matter more than they look:

**The class must be `public`.** Gradle instantiates it reflectively from another class loader. A
package private plugin class compiles perfectly happily and then fails at apply time.

**The `println` goes inside `doLast`, not next to it.** Gradle builds run in two stages. During the
*configuration* stage it evaluates every build script and creates the task graph; during the
*execution* stage it actually runs the tasks you asked for. Code written directly in the task
configuration block runs at configuration time, which means it prints on every single build even
when nobody asked for `dealwithit`. Anything that is the *work* of the task belongs in a
`doLast` block or, better still, in a `@TaskAction` method as shown in
[tutorial 3](3-declaring-tasks-the-right-way.md).

Note also that we call `register` rather than `create`. `register` is lazy: the task object is only
instantiated if something in the build actually needs it. On a large plugin that difference is real
configuration time, and it is what Gradle recommends today.

## Telling Gradle How To Load Your Plugin

We must tell Gradle that `MyPlugin` is the implementation behind a plugin id. You could hand write
`src/main/resources/META-INF/gradle-plugins/io.github.intisy.myplugin.properties`:

```properties
implementation-class=io.github.intisy.MyPlugin
```

Do not. Hand writing that file is the single most common way to break a plugin, because nothing
checks that the class name in it still matches reality. Rename or move the class and the build stays
green while the plugin silently stops loading.

Instead, declare the plugin in your `build.gradle` and let the `java-gradle-plugin` plugin generate
the descriptor for you:

```groovy
plugins {
    id 'java-gradle-plugin'
}

gradlePlugin {
    plugins {
        create('myplugin') {
            id = 'io.github.intisy.myplugin'
            implementationClass = 'io.github.intisy.MyPlugin'
            displayName = 'Gradle Plugin Example'
            description = 'A worked example of a Gradle plugin'
        }
    }
}
```

Now the descriptor is generated at build time, and the `validatePlugins` task that
`java-gradle-plugin` adds will fail the build if `implementationClass` does not resolve.

Give the id a namespace, as above. Bare ids like `myplugin` still work for local use but they
collide with everyone else's, and the Gradle Plugin Portal will not accept them.

## Using the Plugin in a Project

Publish the plugin to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

(The old `gradle install` task was removed years ago along with the `maven` plugin. Applying
`maven-publish` alongside `java-gradle-plugin` gives you `publishToMavenLocal` for free.)

Then consume it from another project. Add the repository in `settings.gradle`, because that is
where Gradle resolves plugins from:

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

and apply it in `build.gradle`:

```groovy
plugins {
    id 'io.github.intisy.myplugin' version '1.0.0'
}
```

The `plugins { }` block replaces the old `buildscript { classpath ... }` incantation. It is
resolvable, it is version aware, and Gradle can reason about it before evaluating the script.

Once this is done you can use the `myplugin` functionality:

```bash
$ gradle dealwithit

> Task :dealwithit
(•_•) ( •_•)>⌐■-■ (⌐■_■)

BUILD SUCCESSFUL in 0s
1 actionable task: 1 executed
```

## Next Steps

That is it. With a small amount of code we have a functioning plugin. We implemented
`Plugin<Project>`, registered a `dealwithit` task, and let `java-gradle-plugin` tell Gradle about
the plugin id.

In the next step we will write a test for this plugin and its one task. Testing is one of the
primary benefits of packaging build logic as a plugin, and we will keep coming back to it.
