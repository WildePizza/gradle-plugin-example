# Troubleshooting

Errors people hit while writing Gradle plugins, what they actually mean, and what to do. Each entry
leads with the message you will see in the console.

## Applying the plugin

### `Plugin with id 'myplugin' not found`

Gradle looked for a plugin descriptor named `myplugin.properties` under `META-INF/gradle-plugins`
and found nothing. Common causes, in order of likelihood:

1. The plugin jar is not on the build's classpath at all. In a consuming project, check that the
   repository is declared in `pluginManagement` in **`settings.gradle`**, not in `build.gradle`.
   Plugins are resolved before `build.gradle` is evaluated, so a `repositories` block there is too
   late.
2. You published a new version but the consumer asks for the old one, or vice versa. Run
   `./gradlew publishToMavenLocal` and check the version in
   `~/.m2/repository/<group>/<artifact>/`.
3. The id has a typo, or you are using the short name where the full namespaced id is required.
   This project's id is `io.github.intisy.myplugin`.

In a TestKit test, this almost always means `withPluginClasspath()` was not called, or the fixture
declares a `version` on the plugin id. With an injected classpath there is nothing to resolve, so
the version must be omitted.

### `Could not find method myplugin() for arguments [...]`

The plugin applied, but the extension was not registered under that name, so the `myplugin { }`
block has nothing to bind to. Check that `apply` calls `getExtensions().create(...)` and that the
name matches the block exactly.

If this appears only in *some* projects, you are probably registering the extension inside a
conditional such as a `withPlugin` callback. Extensions users configure should be registered
unconditionally.

### `Failed to apply plugin 'x'. > Could not create plugin of type 'MyPlugin'`

Almost always one of:

- The plugin class is not `public`. Gradle instantiates it reflectively from a different class
  loader, and a package private class compiles fine but cannot be constructed.
- The class has no no-argument constructor, and no `@Inject` annotated one.
- The class is `abstract` when it should not be, or does not actually implement `Plugin<Project>`.

## Configuration and extensions

### The extension always has its default value

You read the property too early. Anything you `get()` during `apply` runs before the user's
`build.gradle` has been evaluated, so you see the convention rather than what they configured.

Do not fix this by reading the value later by hand. Wire the provider through instead and let Gradle
resolve it at execution time:

```java
task.getFileContent().convention(extension.getFileContent());
```

See [tutorial 5](tutorial/5-making-configurable-plugins.md).

### Setting the extension in `build.gradle` has no effect

You used `set(...)` rather than `convention(...)` when wiring the task. `set` is an explicit value
and takes priority over anything configured afterwards; `convention` is a default that anything else
overrides. In plugin code you almost always want `convention`.

### `Cannot query the value of extension property 'x' because it has no value available`

A `Property` with no convention and nothing assigned. Either give it a convention in the extension
constructor, or mark the task property `@Optional` and handle absence, or fail with a clear message
of your own using `getOrElse` / `isPresent`.

## Tasks

### `Type 'MyTask' must be annotated either with @CacheableTask or with @DisableCachingByDefault`

`validatePlugins` (added by `java-gradle-plugin`) requires every task type to state whether its
output is worth storing in the build cache. Add `@CacheableTask` for expensive reproducible work, or
`@DisableCachingByDefault(because = "...")` when a cache round trip would cost more than just doing
the work.

### The task re-runs every time even though nothing changed

Gradle can only skip a task whose inputs and outputs it knows about. Check that:

- Every input is annotated (`@Input`, `@InputFile`, `@InputFiles`, `@InputDirectory`).
- Every output is annotated (`@OutputFile`, `@OutputDirectory`).
- The task has at least one output. A task that declares none can never be up to date.

Run with `--info` and Gradle will tell you which property it thinks changed.

### The task output changes between runs with identical inputs

Usually iteration order. A `FileCollection`, a `Set`, or a `HashMap` has no guaranteed order, so
writing it out directly produces different bytes each time and defeats up to date checks. Sort
before you write. Timestamps and absolute paths in output cause the same problem.

### `Directory ... is not a valid output` or a file appears in the wrong place

`project.getBuildDir()` is deprecated. Use
`project.getLayout().getBuildDirectory().file("name.txt")`, which returns a provider resolved at
execution time rather than a `File` resolved during configuration.

## Configuration cache

### `Invocation of 'Task.project' at execution time is unsupported`

Something inside a `@TaskAction` reaches back into the `Project` object. That is the single most
common structural mistake in plugin code, and the configuration cache exists partly to catch it.

Capture what you need as task properties during configuration, then read only those properties at
execution time. If a task needs a service, inject it with `@Inject` rather than fetching it from the
project. See [tutorial 3](tutorial/3-declaring-tasks-the-right-way.md).

Adding `--configuration-cache` to your TestKit runs turns this class of bug into a failing test
rather than a user's bug report.

### `Cannot serialize object of type ...`

A task is holding a reference to something that cannot cross a build boundary, commonly a `Project`,
a `Configuration`, a `Task`, or a lambda that captured one of those. Replace the field with a
`Property`, a `Provider`, or a plain serialisable value.

## Testing

### `withPluginClasspath()` produces `Plugin with id ... not found`

The plugin-under-test metadata was not generated, or was generated for a different source set. Check
that `java-gradle-plugin` is applied. It configures this automatically for the `test` source set
only; if you run tests from a custom source set such as `functionalTest`, register it:

```groovy
gradlePlugin {
    testSourceSets(sourceSets.functionalTest)
}
```

### TestKit tests leave `build/` directories in the repository

The runner used a checked-in fixture directory as its project directory. Copy the fixture into a
JUnit `@TempDir` and point `withProjectDir` at the copy, as `TestRealBuild` does here. Cleaning up in
`tearDown` is not equivalent: it does not run when the JVM dies, and a failing test leaves the mess
behind exactly when you most want to inspect it.

### The build under test fails but the message is useless

Use `buildAndFail()` and assert on `result.getOutput()`. Add `--stacktrace` to
`withArguments(...)`. To step through plugin code in a debugger, use `GradleRunner.withDebug(true)`,
which runs the build in the test JVM rather than a forked daemon, then attach to the test JVM as
usual.

### Tests pass locally and fail in CI, or vice versa

Usually file encoding or line endings. Set both explicitly:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

tasks.named('test') {
    defaultCharacterEncoding = 'UTF-8'
}
```

Sorting and path sensitivity (see above) account for most of the rest.

## Task wiring

### `Task ':b' uses this output of task ':a' without declaring an explicit or implicit dependency`

Task `b` reads a file that task `a` declares as an `@OutputFile`, and nothing orders the two. Gradle
refuses to guess, because whether it works would otherwise depend on what happened to be left in
`build/`.

Adding `dependsOn` silences it but leaves the path and the ordering as two facts that can drift
apart. The better fix is to replace the hard coded path with the producer's output provider, which
supplies the implicit dependency the message is asking for:

```java
task.getSources().from(myTask.flatMap(MyTask::getOutputFile));
```

See [tutorial 7](tutorial/7-wiring-tasks-together.md).

### A consumer task reads an empty or stale file

Almost always the same root cause as above, in the case where an ordering rule *does* exist. The
consumer points at a hard coded path while the producer's output was reconfigured elsewhere, so both
tasks run in the right order and read different files. Wire the provider rather than the path.

### `Provider<RegularFileProperty>` where a `Provider<RegularFile>` was expected

`map` where you needed `flatMap`. Use `flatMap` whenever the function you pass itself returns a
provider, which every `get*Property()` getter does.

## Publishing

### The plugin publishes but consumers get `Plugin not found`

The marker artifact did not reach the repository. Publishing a plugin puts *two* things in the
repository: the jar under your normal coordinates, and a small POM under coordinates derived from
the plugin id (`<id>:<id>.gradle.plugin`). The `plugins { }` DSL resolves the marker first and
follows it to the jar, so a repository holding only the jar cannot serve the plugins DSL even though
the classes are right there.

Check that both paths exist in the target repository, and that `java-gradle-plugin` is applied with
the plugin declared in a `gradlePlugin { }` block.

### `Could not resolve plugin ... version` in a consuming build

Plugins are resolved before `build.gradle` is evaluated, so a `repositories { }` block there is too
late. The repository has to be declared in `pluginManagement` in **`settings.gradle`**.

### The Plugin Portal rejects the submission

`website`, `vcsUrl`, `displayName`, `description` and `tags` are all required. Run
`./gradlew publishPlugins --validate-only` to check before you burn a version number; published
versions can be deprecated but never replaced.

## Build script

### `Properties should be assigned using the 'propName = value' syntax`

Gradle 9 deprecates the Groovy DSL space-assignment form. Change `exceptionFormat 'full'` to
`exceptionFormat = 'full'`. This applies to properties only; genuine methods such as
`events 'passed', 'failed'` are unaffected.

### `No such property: X for class: java.lang.String`

Groovy string interpolation. `"$a.B"` parses as the property `B` of `a`, not as `a` followed by the
literal `.B`. Use braces: `"${a}.B"`.

### `Deprecated Gradle features were used in this build`

Run with `--warning-mode all` to see what and where. Note that Gradle reports each deprecation once
per daemon, so a warning can vanish on a second run while the cause is still present. Use
`./gradlew clean build --warning-mode all` when you want the true picture.

## Still stuck?

Open an issue at
<https://github.com/intisy/gradle-plugin-example/issues> with the failing build's
`--stacktrace --info` output and the Gradle version from `./gradlew --version`.
