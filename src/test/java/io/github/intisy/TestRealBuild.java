package io.github.intisy;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRealBuild {
    private static final Path FIXTURES = Path.of(System.getProperty("user.dir"), "testProjects");

    @TempDir
    Path projectDir;

    /**
     * @implNote The fixture is copied rather than built in place so a test run never leaves a
     * {@code build/} directory inside the checked in {@code testProjects/} tree. Every run asks for
     * the configuration cache so a task that reaches back into {@code Project} fails the suite.
     */
    private GradleRunner runnerFor(String fixture, String task) throws IOException {
        copyDirectory(FIXTURES.resolve(fixture), projectDir);
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(task, "--configuration-cache", "--stacktrace");
    }

    private String read(String name) throws IOException {
        return new String(Files.readAllBytes(projectDir.resolve("build").resolve(name)), StandardCharsets.UTF_8);
    }

    @Test
    void dealWithItPrintsTheGreeting() throws IOException {
        BuildResult result = runnerFor("simpleProject", "dealwithit").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":dealwithit").getOutcome());
        assertTrue(result.getOutput().contains("(•_•) ( •_•)>⌐■-■ (⌐■_■)"));
    }

    @Test
    void myTaskWritesTheDefaultContent() throws IOException {
        BuildResult result = runnerFor("simpleProject", "mytask").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mytask").getOutcome());
        assertEquals(MyPluginExtension.DEFAULT_FILE_CONTENT, read("myfile.txt"));
    }

    @Test
    void myOtherTaskWritesItsOwnFile() throws IOException {
        BuildResult result = runnerFor("simpleProject", "myothertask").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":myothertask").getOutcome());
        assertTrue(projectDir.resolve("build/otherfile.txt").toFile().exists());
    }

    @Test
    void myTestableTaskWritesThroughFileCreator() throws IOException {
        BuildResult result = runnerFor("simpleProject", "mytestabletask").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mytestabletask").getOutcome());
        assertEquals(MyPluginExtension.DEFAULT_FILE_CONTENT, read("testablefile.txt"));
    }

    @Test
    void extensionOverridesTheContent() throws IOException {
        BuildResult result = runnerFor("configuredProject", "mytask").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":mytask").getOutcome());
        assertEquals("CONFIGURED", read("myfile.txt"));
    }

    @Test
    void tasksAreUpToDateOnASecondRun() throws IOException {
        GradleRunner runner = runnerFor("simpleProject", "mytask");

        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":mytask").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":mytask").getOutcome());
    }

    @Test
    void checkRunsTheSourceReportInAJavaProject() throws IOException {
        BuildResult result = runnerFor("javaProject", "check").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":sourcereport").getOutcome());
        assertEquals("2 source files", read("sourcereport.txt").lines().findFirst().orElseThrow());
        assertTrue(read("sourcereport.txt").contains("Alpha.java"));
        assertTrue(read("sourcereport.txt").contains("Beta.java"));
    }

    @Test
    void sourceReportIsAbsentWithoutTheJavaPlugin() throws IOException {
        BuildResult result = runnerFor("simpleProject", "tasks").build();

        assertFalse(result.getOutput().contains("sourcereport"));
    }

    /**
     * The point of wiring by provider: nothing declares dependsOn, yet asking for the consumer
     * runs both producers first.
     */
    @Test
    void bundlePullsInItsProducersWithoutDependsOn() throws IOException {
        BuildResult result = runnerFor("simpleProject", "bundle").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":bundle").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":mytask").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":myothertask").getOutcome());

        String bundle = read("bundle.txt");
        assertTrue(bundle.contains("--- myfile.txt ---"));
        assertTrue(bundle.contains("--- otherfile.txt ---"));
    }

    @Test
    void bundleFollowsTheExtensionThroughItsProducers() throws IOException {
        BuildResult result = runnerFor("configuredProject", "bundle").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":bundle").getOutcome());
        assertTrue(read("bundle.txt").contains("CONFIGURED"));
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                Path destination = target.resolve(source.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination);
                }
            }
        }
    }
}
