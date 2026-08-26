# 4: Making Unit Testable Plugins

- [Home](../README.md)
- [Previous](3-declaring-tasks-the-right-way.md)
- [Next](5-making-configurable-plugins.md)

We can now create plugins, add configurable tasks, and test them. There is a problem waiting for us
though: every test we have written so far starts a Gradle build. As the plugin grows, the suite gets
slower, until changing anything is painful. We fix that by decoupling the work from the Gradle
machinery.

In this tutorial we will cover:

- Why tasks are awkward to unit test.
- How to define a task implementation so it can be unit tested.
- How to write fast unit tests against it.

## The Problem with Gradle Tasks

A task type extends `DefaultTask`, and a `DefaultTask` needs a `Project` to exist. Building one
inside a test means `ProjectBuilder`, which is heavy, or TestKit, which is heavier. Neither is
something you want between you and a red or green bar.

Take the task from the previous tutorial:

```java
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

Look at what the body of `action` actually needs: a `File` and a `String`. Everything else is
scaffolding. So we pull those three lines into a class that knows nothing about Gradle, and let the
task be a thin adapter.

## Creating a Task Implementation Class

```java
package io.github.intisy.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileCreator {
    private final File outputFile;
    private final String content;

    public FileCreator(File outputFile, String content) {
        this.outputFile = outputFile;
        this.content = content;
    }

    public void create() throws IOException {
        Path target = outputFile.toPath();
        Files.createDirectories(target.getParent());
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
    }
}
```

A few things worth pointing out:

1. **There is no dependency on Gradle.** Only the JDK. This will be trivial to test.
2. **Everything it needs arrives through the constructor.** No lookups, no ambient state.
3. **It is trivially constructible from inside a task.** Users will not notice a difference.

Putting it in an `impl` package is a useful convention: it signals that the class is not part of the
plugin's public surface, so you can change it without breaking anyone's build script.

## Making our Tasks Simpler

The task now reduces to:

```java
package io.github.intisy;

import io.github.intisy.impl.FileCreator;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;

@DisableCachingByDefault(because = "Writing a handful of bytes is cheaper than a cache round trip")
public abstract class MyTestableTask extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getFileContent();

    @TaskAction
    public void action() throws IOException {
        new FileCreator(getOutputFile().get().getAsFile(), getFileContent().get()).create();
    }
}
```

The job of the task class is now only to:

1. Expose configuration to the user.
2. Declare inputs and outputs so Gradle can skip the task when nothing changed.
3. Unwrap the properties and hand plain values to the implementation.

That is a small enough job that not being able to unit test it stops mattering. The integration test
from tutorial 2 covers the wiring; the unit tests below cover the behaviour.

Note the exact placement of the `get()` calls. They happen inside `@TaskAction`, which is execution
time, so the values are whatever the user finally configured. Calling `get()` during configuration
would read the value too early, before the build script has been evaluated. That distinction is the
subject of the next tutorial.

## Testing the `FileCreator`

Now the payoff. These tests need no `Project`, no build, and no TestKit:

```java
package io.github.intisy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFileCreator {
    @TempDir
    Path tempDir;

    @Test
    void createsFileWithContent() throws IOException {
        File target = tempDir.resolve("out.txt").toFile();

        new FileCreator(target, "HELLO FROM MY PLUGIN").create();

        assertTrue(target.exists());
        assertEquals("HELLO FROM MY PLUGIN", Files.readString(target.toPath()));
    }

    @Test
    void createsMissingParentDirectories() throws IOException {
        File target = tempDir.resolve("deeply/nested/out.txt").toFile();

        new FileCreator(target, "HELLO FROM MY PLUGIN").create();

        assertTrue(target.exists());
        assertEquals("HELLO FROM MY PLUGIN", Files.readString(target.toPath()));
    }

    @Test
    void overwritesAnExistingFile() throws IOException {
        File target = tempDir.resolve("out.txt").toFile();
        Files.writeString(target.toPath(), "STALE");

        new FileCreator(target, "FRESH").create();

        assertEquals("FRESH", Files.readString(target.toPath()));
    }
}
```

JUnit 5's `@TempDir` gives each test a fresh directory and deletes it afterwards, so there is no
`setUp` or `tearDown` to forget. Compare this with the older pattern of `File.createTempFile` plus
`deleteOnExit`, which leaves files around for the lifetime of the JVM and shares state between
tests.

Corner cases are now cheap to cover. Missing parents, existing files, empty content, character
encoding, a path that is a directory: each is a few lines and runs in microseconds. That is the kind
of coverage you will never build up if every case costs you a Gradle build.

## Where the Line Sits

A useful rule of thumb for deciding what goes in which test:

| Question | Test |
| --- | --- |
| Does the logic do the right thing? | Unit test the `impl` class |
| Is the task registered, named, typed, configured? | `ProjectBuilder` test |
| Does the whole thing work when a build script applies it? | TestKit integration test |

Aim for many of the first, some of the second, and few of the third.

## Next Steps

We have separated the plugin's logic from the Gradle infrastructure, which lets us test that logic
with ordinary fast unit tests rather than full builds.

In the next tutorial we will make the plugin configurable with a Gradle extension, and cover the
project lifecycle rules that decide when configuration may be read.
