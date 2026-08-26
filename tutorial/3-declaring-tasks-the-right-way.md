# 3: Declaring Tasks the Right Way

- [Home](../README.md)
- [Previous](2-your-first-plugin-test.md)
- [Next](4-making-unit-testable-plugins.md)

Implementing tasks inline in your top level plugin class rapidly grows out of control. As you add
tasks it becomes worth declaring each one as its own type.

In this tutorial we will cover:

- How to define a task in its own file.
- How to make a task configurable with lazy properties.
- How to register the task with a project.
- How to add a group and description to your task.
- How to test that tasks are added correctly.

## Declaring a Task

Declaring a task as a separate type is easy. Extend `DefaultTask` and implement a method annotated
with `@TaskAction`.

We will implement a task that writes a file:

```java
package io.github.intisy;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@DisableCachingByDefault(because = "Writing a handful of bytes is cheaper than a cache round trip")
public abstract class MyTask extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getFileContent();

    @TaskAction
    public void action() throws IOException {
        Path target = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(target.getParent());
        Files.write(target, getFileContent().get().getBytes(StandardCharsets.UTF_8));
    }
}
```

There is a lot packed into that, so let us take it apart.

### Abstract classes and managed properties

The class is `abstract`, and so are its getters. You never write the fields or the setters. Gradle
generates an implementation at runtime and injects a real `Property` object for each abstract getter
it finds. This is called a *managed property*, and it is why there is no constructor here.

### Lazy properties instead of plain fields

The older way to write this was a plain field:

```java
// Don't do this
private File outputFile = new File(getProject().getBuildDir(), "myfile.txt");
```

`Property<T>` and `RegularFileProperty` replace that, and they buy you three things:

1. **Deferred evaluation.** A property can hold a *provider* of a value rather than a value. Nothing
   is computed until somebody calls `get()`, which happens at execution time. That is what lets a
   task be wired to an extension before the user's build script has been read. Tutorial 5 leans on
   this heavily.
2. **Wiring, not copying.** `taskA.getInput().set(taskB.getOutput())` also tells Gradle that `taskA`
   depends on `taskB`. With plain `File` fields you have to declare `dependsOn` by hand and keep it
   in sync.
3. **Configuration cache compatibility.** A property carries its value with it, so the task never
   has to reach back into `Project` while it runs.

### Input and output annotations

`@Input` and `@OutputFile` are what make the task *incremental*. Gradle hashes the declared inputs
and outputs; if nothing changed since last time, the task is skipped and reported `UP_TO_DATE`.
A task with no annotated properties can never be up to date, so it re-runs on every build.

Use `@Input` for simple values, `@InputFile` and `@InputFiles` for files you read, `@OutputFile` and
`@OutputDirectory` for what you produce, and `@Internal` for anything that is neither.

### `@DisableCachingByDefault`

`java-gradle-plugin` adds a `validatePlugins` task that fails the build if a task type does not say
whether it is worth caching in the build cache. Annotate with `@CacheableTask` when the work is
expensive and reproducible, or `@DisableCachingByDefault(because = "...")` when it is not. Writing a
few bytes to disk costs less than a cache lookup, so ours is disabled with a reason.

## Registering the Task

Once a task type exists it must be registered on the project under a name. This goes in your top
level plugin class:

```java
package io.github.intisy;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("mytask", MyTask.class, task ->
                task.getOutputFile().convention(
                        project.getLayout().getBuildDirectory().file("myfile.txt")));
    }
}
```

`getLayout().getBuildDirectory()` is the modern replacement for `project.getBuildDir()`, which is
deprecated. It returns a `DirectoryProperty`, so `file("myfile.txt")` gives you a `Provider` that is
resolved later rather than a `File` resolved right now.

`convention(...)` sets a default that the user can still override. Use it instead of `set(...)` for
defaults: `set` wins over anything the build script does, which makes your task look broken to
whoever tries to configure it.

## Reusing a Task Type

Because the output path is configurable, one task type covers several task instances:

```java
project.getTasks().register("mytask", MyTask.class, task ->
        task.getOutputFile().convention(
                project.getLayout().getBuildDirectory().file("myfile.txt")));

project.getTasks().register("myothertask", MyTask.class, task ->
        task.getOutputFile().convention(
                project.getLayout().getBuildDirectory().file("otherfile.txt")));
```

## Adding Group and Description

A group and a description help users find your task. They are set the same way as any other task
property:

```java
project.getTasks().register("mytask", MyTask.class, task -> {
    task.setGroup("MyPlugin");
    task.setDescription("Create myfile.txt in the build directory");
    task.getOutputFile().convention(
            project.getLayout().getBuildDirectory().file("myfile.txt"));
});
```

You will see this when listing tasks with `gradle tasks`:

```
$ gradle tasks

------------------------------------------------------------
Tasks runnable from root project 'simpleProject'
------------------------------------------------------------

MyPlugin tasks
--------------
dealwithit - Print the plugin's greeting
myothertask - Create otherfile.txt in the build directory
mytask - Create myfile.txt in the build directory
mytestabletask - Create testablefile.txt using the unit testable FileCreator
```

A task without a group only shows under `gradle tasks --all`, which is a reasonable choice for
helper tasks users are not meant to invoke directly.

## Testing that Tasks are Added Properly

We can now unit test part of the plugin without running a build, using `ProjectBuilder`:

```java
package io.github.intisy;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestMyPlugin {
    private Project project;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().withName("hello-world").build();
        project.getPluginManager().apply(MyPlugin.class);
    }

    private File buildFile(String name) {
        return project.getLayout().getBuildDirectory().file(name).get().getAsFile();
    }

    @Test
    void addsEveryTask() {
        assertNotNull(project.getTasks().findByName("dealwithit"));
        assertNotNull(project.getTasks().findByName("mytask"));
        assertNotNull(project.getTasks().findByName("myothertask"));
    }

    @Test
    void myTaskWritesMyFile() {
        MyTask task = assertInstanceOf(MyTask.class, project.getTasks().findByName("mytask"));
        assertEquals(buildFile("myfile.txt"), task.getOutputFile().get().getAsFile());
    }
}
```

This covers three things that matter:

1. Tasks are registered with the project under the expected names.
2. Tasks are of the expected type.
3. Tasks carry the expected configuration.

Note that `findByName` forces the task to be created, which defeats the laziness of `register`.
That is fine and correct in a test, where you are deliberately asking to look at the task. In
production plugin code, prefer `named(...)` so you do not realise tasks nobody asked for.

## Testing the Task Itself

Testing what the task *does* is harder, because a `DefaultTask` subclass needs a full `Project` to
construct. For now we test it through the integration test bench from tutorial 2:

```java
@Test
void myOtherTaskWritesItsOwnFile() throws IOException {
    BuildResult result = runnerFor("simpleProject", "myothertask").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":myothertask").getOutcome());
    assertTrue(projectDir.resolve("build/otherfile.txt").toFile().exists());
}
```

Because our task declares its inputs and outputs, we can also assert the incremental behaviour,
which is the part people most often get wrong:

```java
@Test
void tasksAreUpToDateOnASecondRun() throws IOException {
    GradleRunner runner = runnerFor("simpleProject", "mytask");

    assertEquals(TaskOutcome.SUCCESS, runner.build().task(":mytask").getOutcome());
    assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":mytask").getOutcome());
}
```

If that second assertion fails, an input or output annotation is missing.

## Next Steps

We have learned to define tasks as reusable types, make them configurable through lazy properties,
declare their inputs and outputs so Gradle can skip them, and document them for users.

In the next tutorial we will cover how to split a task so that its logic can be unit tested, without
a `Project` in sight.
