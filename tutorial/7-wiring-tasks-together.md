# 7: Wiring Tasks Together

- [Home](../README.md)
- [Previous](6-reacting-to-other-plugins.md)
- [Next](8-publishing-your-plugin.md)

Real plugins rarely consist of independent tasks. One task produces something another consumes, and
Gradle has to run them in the right order without you telling it twice.

In this tutorial we will cover:

- Why `dependsOn` is usually the wrong tool.
- How wiring a property carries the task dependency for free.
- `map`, `flatMap` and `zip` on providers.
- When ordering rules such as `mustRunAfter` and `finalizedBy` are appropriate.

## The Problem with `dependsOn`

Suppose `bundle` concatenates the files that `mytask` and `myothertask` produce. The obvious first
attempt:

```java
// Don't do this
project.getTasks().register("bundle", BundleTask.class, task -> {
    task.dependsOn("mytask", "myothertask");
    task.getSources().from(
            new File(project.getBuildDir(), "myfile.txt"),
            new File(project.getBuildDir(), "otherfile.txt"));
});
```

This has the same fact written down twice: once as an ordering rule, once as a path. Every way that
can drift, it will.

- Someone configures `mytask.outputFile` to a different location. `bundle` still reads the old path,
  finds nothing, and produces an empty bundle. No error.
- Someone renames the task. The string `"mytask"` still resolves at configuration time to nothing
  useful, or fails late.
- Someone removes the `dependsOn` while tidying. The build now works or fails depending on task
  ordering and what happens to be left in `build/` from a previous run. This is the worst kind of
  bug, because it passes locally and fails on a clean CI checkout.

The paths are also resolved eagerly with the deprecated `getBuildDir()`, so they cannot follow
configuration made later in the build script.

## Wiring Instead

Hand the consumer the producer's *output property*, not a path:

```java
TaskProvider<MyTask> myTask = project.getTasks()
        .register("mytask", MyTask.class, task -> { ... });

TaskProvider<MyTask> myOtherTask = project.getTasks()
        .register("myothertask", MyTask.class, task -> { ... });

project.getTasks().register("bundle", BundleTask.class, task -> {
    task.setGroup(GROUP);
    task.setDescription("Concatenate the other tasks' output into bundle.txt");
    task.getSources().from(
            myTask.flatMap(MyTask::getOutputFile),
            myOtherTask.flatMap(MyTask::getOutputFile));
    task.getOutputFile().convention(
            project.getLayout().getBuildDirectory().file("bundle.txt"));
});
```

There is no `dependsOn` anywhere, and yet `gradle bundle` runs `mytask` and `myothertask` first.

This is the single most useful thing to understand about Gradle's provider API. A `Provider` created
from a task's output property carries *provenance*: it knows which task produces the value. When you
attach it to another task's input, Gradle records both the value and the dependency. One statement,
one source of truth, and a rename or a reconfiguration cannot desynchronise them.

The test that demonstrates it:

```java
@Test
void bundlePullsInItsProducersWithoutDependsOn() throws IOException {
    BuildResult result = runnerFor("simpleProject", "bundle").build();

    assertEquals(TaskOutcome.SUCCESS, result.task(":bundle").getOutcome());
    assertEquals(TaskOutcome.SUCCESS, result.task(":mytask").getOutcome());
    assertEquals(TaskOutcome.SUCCESS, result.task(":myothertask").getOutcome());
}
```

Note also that configuration keeps flowing through the chain. `bundle` was never told about the
`myplugin { }` extension, but because its inputs come from tasks that were wired to it, setting
`fileContent` changes the bundle:

```java
@Test
void bundleFollowsTheExtensionThroughItsProducers() throws IOException {
    BuildResult result = runnerFor("configuredProject", "bundle").build();

    assertTrue(read("bundle.txt").contains("CONFIGURED"));
}
```

## `map`, `flatMap` and `zip`

`flatMap` above is not arbitrary. The rule is straightforward once you see the types:

| You have | You want | Use |
| --- | --- | --- |
| `Provider<T>` | `Provider<R>` from a plain value | `map` |
| `Provider<T>` | `Provider<R>` where the function itself returns a provider | `flatMap` |
| Two providers | one combined provider | `zip` |

`TaskProvider<MyTask>.flatMap(MyTask::getOutputFile)` uses `flatMap` because `getOutputFile()`
returns a `RegularFileProperty`, which is itself a provider. Using `map` there would give you a
`Provider<RegularFileProperty>`, which is a nesting mistake the compiler will catch.

```java
// Derive a value from another task's output
Provider<String> name = myTask.flatMap(MyTask::getOutputFile)
        .map(file -> file.getAsFile().getName());

// Combine two
Provider<String> both = extension.getFileContent()
        .zip(name, (content, fileName) -> fileName + ": " + content);
```

All of these stay lazy. Nothing is computed until a task actually needs the value, and the
dependency information travels along with it.

## When Ordering Rules Are Right

Property wiring only applies when data flows between tasks. When it does not, you need an explicit
rule, and Gradle offers four:

| Rule | Meaning |
| --- | --- |
| `dependsOn` | B requires A to have run. Forces A to run whenever B does. |
| `finalizedBy` | Run B after A, even if A failed. For cleanup and reporting. |
| `mustRunAfter` | *If* both are scheduled, order them. Does not force either to run. |
| `shouldRunAfter` | Same, but Gradle may ignore it to break a cycle or improve parallelism. |

`dependsOn` is correct for lifecycle tasks, as in [tutorial 6](6-reacting-to-other-plugins.md) where
`check` gained a dependency on `sourcereport`. Nothing flows between them; `check` simply has to
trigger it.

`mustRunAfter` is the one people reach for when they should be wiring properties. If you find
yourself writing it to make sure a file exists before another task reads it, that is a data
dependency wearing a disguise, and it will break the first time somebody runs the second task on its
own.

## The Error You Will See

Get this wrong and Gradle tells you, though the message takes a moment to parse:

```
Reason: Task ':bundle' uses this output of task ':mytask' without declaring an explicit
or implicit dependency.
```

Gradle noticed that `bundle` reads a file another task declares as its `@OutputFile`, and that
nothing orders them. It is not asking you to add `dependsOn`. The better fix is almost always to
replace the hard coded path with the producer's output provider, which supplies the "implicit
dependency" the message refers to.

## Why This Matters More Than It Looks

Task wiring is what makes the rest of Gradle work:

- **Correctness.** The task graph is derived from real data flow rather than from a human keeping
  two lists in sync.
- **Up to date checks.** Because `bundle` declares its inputs as the producers' outputs, changing
  `fileContent` invalidates exactly the tasks that depend on it and nothing else.
- **Parallelism.** Gradle can only run tasks concurrently when it knows they are independent.
  Over-declared `dependsOn` edges serialise builds that did not need to be serial.
- **Configuration cache.** A wired provider is serialisable. A captured `Project` or `Task`
  reference is not.

## Next Steps

The plugin now has a task graph rather than a pile of tasks, built from data flow rather than
declarations that can drift apart.

That completes the plugin itself. In the final tutorial we will publish it, so that other projects
can actually use what you have built.
