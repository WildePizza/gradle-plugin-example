# 6: Reacting to Other Plugins

- [Home](../README.md)
- [Previous](5-making-configurable-plugins.md)
- [Next](7-wiring-tasks-together.md)

Almost every plugin worth writing has to cooperate with another one. You want to add a check to the
`java` lifecycle, read the source sets a project declared, or configure something only when the
`application` plugin is present.

The naive approach is to apply the other plugin yourself. That is almost always wrong, and this
tutorial is mostly about why. We will cover:

- Why applying another plugin from your own is a trap.
- How to react to a plugin instead, with `pluginManager.withPlugin`.
- How to read another plugin's model safely.
- How to hook your task into a lifecycle task such as `check`.
- How to test both the present and absent cases.

## The Trap

Say we want a task that summarises a project's Java sources. It needs source sets, which come from
the `java` plugin. So we make sure the plugin is there:

```java
// Don't do this
public void apply(Project project) {
    project.getPluginManager().apply("java");

    SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
    ...
}
```

This compiles, and it appears to work. It has two serious problems.

**It forces `java` onto everyone.** Someone applying your plugin to a documentation project, an
Android project, or a Kotlin-only project now has a Java plugin they never asked for, along with all
of its tasks and conventions. Your plugin has stopped being additive.

**It is a race you can lose.** Consider what happens with a slightly different guard:

```java
// This is also wrong, and more subtly
if (project.getPluginManager().hasPlugin("java")) {
    ...
}
```

Plugins are applied in the order the build script lists them. If the user writes

```groovy
plugins {
    id 'io.github.intisy.myplugin'
    id 'java'
}
```

then at the moment your `apply` runs, `java` has not been applied yet, `hasPlugin` returns false,
and your task silently never appears. Swap the two lines and it works. A plugin whose behaviour
depends on the order of an unordered list is a plugin that generates bug reports you cannot
reproduce.

## Reacting Instead

The fix is to register a callback and let Gradle invoke it whenever the other plugin shows up,
whether that is before or after your own:

```java
project.getPluginManager().withPlugin("java", applied -> {
    // runs once, as soon as the java plugin is applied
    // never runs at all if it is not
});
```

That single call solves both problems. Nothing is forced on anyone, and both orderings behave
identically. If `java` is never applied, the callback simply never fires and your plugin quietly
does less.

Here is the real thing from this repository:

```java
private void addJavaIntegration(Project project) {
    project.getPluginManager().withPlugin("java", applied -> {
        TaskProvider<SourceReportTask> report = project.getTasks()
                .register("sourcereport", SourceReportTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Summarise the main source set into sourcereport.txt");
                    SourceSetContainer sourceSets = project.getExtensions()
                            .getByType(SourceSetContainer.class);
                    task.getSources().from(sourceSets.getByName("main").getAllJava());
                    task.getOutputFile().convention(
                            project.getLayout().getBuildDirectory().file("sourcereport.txt"));
                });

        project.getTasks().named("check").configure(check -> check.dependsOn(report));
    });
}
```

Note that `getByType(SourceSetContainer.class)` is safe *here* and would not have been in `apply`.
Inside the callback the `java` plugin is guaranteed to have been applied already, so its extension
is guaranteed to exist.

Use the plugin *id*, not the class, in `withPlugin`. The id is the stable public identifier;
`JavaPlugin.class` forces the class to load and ties you to a particular Gradle internal.

## Hooking Into a Lifecycle Task

`check`, `build`, `assemble`, and `clean` are *lifecycle* tasks. They do no work themselves; they
exist so that other tasks can attach to them. Adding your task to `check` means it runs as part of
`gradle check` and `gradle build`, which is how a verification task gets used in practice:

```java
project.getTasks().named("check").configure(check -> check.dependsOn(report));
```

Two details:

- **`named(...).configure(...)`, not `getByName(...)`.** `named` returns a provider and defers the
  configuration; `getByName` forces the task to be created immediately. Inside a `withPlugin`
  callback the difference is small, but the habit matters in a plugin that registers many tasks.
- **`dependsOn(report)` takes the `TaskProvider`,** not a name string. Passing the provider keeps
  the dependency typed and lazy. Passing `"sourcereport"` also works, and silently does nothing
  useful if you ever misspell it.

Wiring by *property* is better still where it applies. If `check` consumed the report file, you
would write `other.getInput().set(report.flatMap(SourceReportTask::getOutputFile))` and get the task
dependency for free, as covered in [tutorial 3](3-declaring-tasks-the-right-way.md). `dependsOn` is
the right tool only when there is no data flowing between the two tasks, which is exactly the case
for a lifecycle task.

## The Task Itself

Nothing here is new; it follows the pattern from tutorials 3 and 4. The only novelty is
`ConfigurableFileCollection` for a set of input files:

```java
@DisableCachingByDefault(because = "Writing a handful of bytes is cheaper than a cache round trip")
public abstract class SourceReportTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void action() throws IOException {
        new SourceReporter(getSources().getFiles(), getOutputFile().get().getAsFile()).report();
    }
}
```

`@PathSensitive(PathSensitivity.RELATIVE)` tells Gradle that only the relative paths of the inputs
matter, not their absolute location. Without it, checking out the project into a different directory
invalidates the task. With it, up to date checks and the build cache keep working across machines.

And, per tutorial 4, the actual work lives in a Gradle-free class:

```java
public class SourceReporter {
    public String render() {
        List<String> names = new ArrayList<>();
        for (File source : sources) {
            names.add(source.getName());
        }
        Collections.sort(names);
        ...
    }
}
```

That `Collections.sort` is not cosmetic. A `FileCollection` has no guaranteed iteration order, so an
unsorted report would produce different bytes on different runs, and the task could never be up to
date. Any time a task's output depends on iteration order, sort it.

## Testing Both Paths

The whole point of reacting rather than applying is that the plugin behaves correctly in two
different worlds, so test both. `ProjectBuilder` is enough and needs no build:

```java
@Test
void doesNotAddSourceReportWithoutTheJavaPlugin() {
    assertNull(project.getTasks().findByName("sourcereport"));
}

@Test
void addsSourceReportWhenJavaIsAppliedFirst() {
    Project javaProject = ProjectBuilder.builder().withName("java-first").build();
    javaProject.getPluginManager().apply("java");
    javaProject.getPluginManager().apply(MyPlugin.class);

    assertNotNull(javaProject.getTasks().findByName("sourcereport"));
}

@Test
void addsSourceReportWhenJavaIsAppliedLast() {
    Project javaProject = ProjectBuilder.builder().withName("java-last").build();
    javaProject.getPluginManager().apply(MyPlugin.class);
    javaProject.getPluginManager().apply("java");

    assertNotNull(javaProject.getTasks().findByName("sourcereport"));
}
```

That third test is the one that earns its keep. It is the test that fails if somebody later
"simplifies" the `withPlugin` callback into an `if (hasPlugin(...))`.

To assert the lifecycle hook, resolve the dependency properly rather than inspecting
`getDependsOn()`, which holds providers rather than tasks:

```java
@Test
void hooksSourceReportIntoCheck() {
    Project javaProject = ProjectBuilder.builder().withName("checked").build();
    javaProject.getPluginManager().apply("java");
    javaProject.getPluginManager().apply(MyPlugin.class);

    Task check = javaProject.getTasks().getByName("check");
    assertTrue(check.getTaskDependencies().getDependencies(check).stream()
            .anyMatch(task -> task.getName().equals("sourcereport")));
}
```

Finally, an integration test against a fixture at `testProjects/javaProject` that applies both
plugins and contains two source files:

```java
@Test
void checkRunsTheSourceReportInAJavaProject() throws IOException {
    BuildResult result = runnerFor("javaProject", "check").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":sourcereport").getOutcome());
    assertEquals("2 source files", read("sourcereport.txt").lines().findFirst().orElseThrow());
}

@Test
void sourceReportIsAbsentWithoutTheJavaPlugin() throws IOException {
    BuildResult result = runnerFor("simpleProject", "tasks").build();

    assertFalse(result.getOutput().contains("sourcereport"));
}
```

## Reacting to Other Things

`withPlugin` is the most common case, but the same "react, do not assume" principle appears
throughout the Gradle API:

| You want to react to | Use |
| --- | --- |
| A plugin being applied | `pluginManager.withPlugin("java") { }` |
| Any task of a type being registered | `tasks.withType(MyTask.class).configureEach { }` |
| An element being added to a container | `sourceSets.configureEach { }` |
| A specific task, if it exists | `tasks.matching { it.name == "x" }.configureEach { }` |

All of these are lazy and order independent, and all of them are preferable to iterating a container
during `apply` and hoping everything is already in it. The `configureEach` variants in particular
only run for elements that are actually realised, so they cost nothing for tasks nobody invokes.

## Next Steps

Your plugin can now cooperate with the rest of the build rather than fighting it. Combined with the
earlier tutorials you have the full toolkit: task types with lazy properties, a testable
implementation, user configuration through an extension, and integration with whatever else the
project applies.

In the next tutorial we will connect the plugin's own tasks to each other, so that Gradle derives
the task graph from real data flow rather than from ordering rules you have to maintain by hand.

If something goes wrong along the way, [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) collects the
errors people hit most often and what they actually mean.
