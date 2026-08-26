# 5: Making Configurable Plugins

- [Home](../README.md)
- [Previous](4-making-unit-testable-plugins.md)
- [Next](6-reacting-to-other-plugins.md)

When a plugin is applied to a lot of projects, something will inevitably differ between them. An
artifact name, an output directory, a behaviour toggle. Gradle provides *extensions* for exactly
this.

In this tutorial we will cover:

- How to define an extension for our plugin.
- The project lifecycle, and when configuration may be read.
- How a task reads the extension without falling into the lifecycle trap.
- How users set configuration in `build.gradle`.
- How to test a plugin's configuration.

## Defining an Extension

An extension is an abstract class whose configurable values are `Property` objects:

```java
package io.github.intisy;

import org.gradle.api.provider.Property;

public abstract class MyPluginExtension {
    public static final String NAME = "myplugin";

    public static final String DEFAULT_FILE_CONTENT = "¯\\_(ツ)_/¯";

    public abstract Property<String> getFileContent();

    public MyPluginExtension() {
        getFileContent().convention(DEFAULT_FILE_CONTENT);
    }
}
```

Just like a task type, the class is abstract and Gradle generates the implementation, injecting a
real `Property` for each abstract getter. The constructor sets the default via `convention`, so a
build script that says nothing still gets a sensible value.

Registering it on the project is one line:

```java
MyPluginExtension extension = project.getExtensions()
        .create(MyPluginExtension.NAME, MyPluginExtension.class);
```

Use `create`, not `add`. `create` asks Gradle to instantiate the class, which is what makes the
managed properties work; `add` takes an object you built yourself and gives you none of that.

## Deep(ish) Dive: the Gradle Project Lifecycle

You may be wondering when it is safe to read that configuration. It helps to review Gradle's
documentation on the [build lifecycle](https://docs.gradle.org/current/userguide/build_lifecycle.html),
but the short version is that a build runs in three phases:

- **Initialisation** decides which projects take part.
- **Configuration** evaluates every build script and builds the task graph. Your `apply` method runs
  here, and so does the user's `myplugin { }` block. Crucially, *your plugin is applied before the
  rest of the build script is read*, so at the moment `apply` returns, the user has not configured
  anything yet.
- **Execution** runs the tasks that were requested. Every `@TaskAction` happens here.

This is the trap that catches everyone:

```java
// Don't do this. It reads the default, always.
public void apply(Project project) {
    MyPluginExtension extension = project.getExtensions()
            .create(MyPluginExtension.NAME, MyPluginExtension.class);

    String content = extension.getFileContent().get();   // too early
    ...
}
```

The classic fix was to defer the read to `afterEvaluate`, which runs once the build script has been
fully evaluated:

```java
// Works, but you rarely need it any more.
project.afterEvaluate(proj ->
        System.out.println(extension.getFileContent().get()));
```

`afterEvaluate` still exists and still works, but it is a blunt instrument. It creates ordering
problems between plugins, it is easy to register twice, and it does not compose. Most plugins written
today do not need it at all, because providers solve the same problem more directly.

## Reading the Extension the Modern Way

Rather than reading a value at some carefully chosen moment, wire the task's property *to* the
extension's property and let Gradle resolve it when the task runs:

```java
public class MyPlugin implements Plugin<Project> {
    public static final String GROUP = "MyPlugin";

    @Override
    public void apply(Project project) {
        MyPluginExtension extension = project.getExtensions()
                .create(MyPluginExtension.NAME, MyPluginExtension.class);

        project.getTasks().register("mytask", MyTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Create myfile.txt in the build directory");
            task.getOutputFile().convention(
                    project.getLayout().getBuildDirectory().file("myfile.txt"));
            task.getFileContent().convention(extension.getFileContent());
        });
    }
}
```

`convention(extension.getFileContent())` does not read anything. It hands the task a *provider*, a
recipe for getting the value later. Nothing is resolved until the task's `@TaskAction` calls `get()`
at execution time, by which point the user's `myplugin { }` block has long since been applied.

There is no lifecycle rule left to remember, and no `afterEvaluate`. This also keeps the task
configuration cache compatible, because the task holds its own value rather than a reference back
to the `Project`.

Using `convention` rather than `set` matters here too. It leaves room for someone to configure a
single task directly, overriding the plugin-wide extension value:

```groovy
myplugin {
    fileContent = 'FROM THE EXTENSION'
}

tasks.named('myothertask') {
    fileContent = 'JUST THIS ONE TASK'
}
```

## Setting Configuration in `build.gradle`

Using the extension will look familiar, because you have been doing it all along with Gradle's
built in plugins:

```groovy
plugins {
    id 'io.github.intisy.myplugin' version '1.0.0'
}

myplugin {
    fileContent = 'OMGWTFBBQ'
}
```

If the `myplugin` block is absent the convention applies, and the file gets the default content.

## Testing with the Extension

The cheapest test asserts that the extension is registered with the right default:

```java
@Test
void registersExtensionWithDefaultContent() {
    MyPluginExtension extension = project.getExtensions().getByType(MyPluginExtension.class);
    assertNotNull(extension);
    assertEquals(MyPluginExtension.DEFAULT_FILE_CONTENT, extension.getFileContent().get());
}
```

Prefer `getByType` over `getByName` where you can: it returns a typed object rather than an
`Object` you have to cast, so a rename breaks the compile instead of the test run.

More interesting is asserting that the wiring works, which `ProjectBuilder` can do without a build:

```java
@Test
void tasksFollowTheExtension() {
    project.getExtensions().getByType(MyPluginExtension.class).getFileContent().set("OMGWTFBBQ");

    MyTask task = (MyTask) project.getTasks().getByName("mytask");
    assertEquals("OMGWTFBBQ", task.getFileContent().get());
}
```

Note that the extension is set *after* the plugin was applied, exactly as it would be in a real
build script, and the task still sees the new value. That test fails if you ever replace the
provider wiring with an eager read.

Finally, an integration test proves the `myplugin` block is honoured for real. Using a second
fixture at `testProjects/configuredProject`:

```groovy
plugins {
    id 'io.github.intisy.myplugin'
}

myplugin {
    fileContent = 'CONFIGURED'
}
```

```java
@Test
void extensionOverridesTheContent() throws IOException {
    BuildResult result = runnerFor("configuredProject", "mytask").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":mytask").getOutcome());
    assertEquals("CONFIGURED",
            Files.readString(projectDir.resolve("build/myfile.txt")));
}
```

## Passing Configuration Down to the Implementation

Tutorial 4 left `FileCreator` with a hard coded string. Now that content is configurable, it becomes
an ordinary constructor argument:

```java
public FileCreator(File outputFile, String content) {
    this.outputFile = outputFile;
    this.content = content;
}
```

and the task unwraps the property on its way in:

```java
@TaskAction
public void action() throws IOException {
    new FileCreator(getOutputFile().get().getAsFile(), getFileContent().get()).create();
}
```

This is the shape to aim for. The extension holds configuration, the plugin wires it, the task
unwraps it, and the implementation receives plain values and knows nothing about any of it. Each
layer is testable on its own terms.

## Next Steps

We have made the plugin configurable with an extension, and seen why lazy providers are a better
answer to the lifecycle problem than `afterEvaluate`.

In the next tutorial we will make the plugin cooperate with other plugins, which is what
turns it from a self contained example into something that fits into a real build.

There is far more you can do with plugins; the
[Gradle User Manual](https://docs.gradle.org/current/userguide/userguide.html) and the
[Gradle API reference](https://docs.gradle.org/current/javadoc/) are the places to go next, and
the chapter on
[developing custom plugins](https://docs.gradle.org/current/userguide/custom_plugins.html) covers
publishing to the Gradle Plugin Portal.

You may also fork this project and use it as a starting point for a new plugin. Everything shown in
these five tutorials is built and tested here, so `./gradlew build` is a working reference for all
of it.
