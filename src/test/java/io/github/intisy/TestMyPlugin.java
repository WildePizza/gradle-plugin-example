package io.github.intisy;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNotNull(project.getTasks().findByName("mytestabletask"));
    }

    @Test
    void myTaskWritesMyFile() {
        MyTask task = assertInstanceOf(MyTask.class, project.getTasks().findByName("mytask"));
        assertEquals(buildFile("myfile.txt"), task.getOutputFile().get().getAsFile());
    }

    @Test
    void myOtherTaskWritesOtherFile() {
        MyTask task = assertInstanceOf(MyTask.class, project.getTasks().findByName("myothertask"));
        assertEquals(buildFile("otherfile.txt"), task.getOutputFile().get().getAsFile());
    }

    @Test
    void tasksAreGroupedAndDescribed() {
        project.getTasks().named("mytask").configure(task -> {
            assertEquals(MyPlugin.GROUP, task.getGroup());
            assertNotNull(task.getDescription());
        });
    }

    @Test
    void registersExtensionWithDefaultContent() {
        MyPluginExtension extension = project.getExtensions().getByType(MyPluginExtension.class);
        assertNotNull(extension);
        assertEquals(MyPluginExtension.DEFAULT_FILE_CONTENT, extension.getFileContent().get());
    }

    @Test
    void tasksFollowTheExtension() {
        project.getExtensions().getByType(MyPluginExtension.class).getFileContent().set("OMGWTFBBQ");

        MyTask task = (MyTask) project.getTasks().getByName("mytask");
        assertEquals("OMGWTFBBQ", task.getFileContent().get());
    }

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

    /**
     * The order plugins are applied in is the user's choice, not ours. Reacting with
     * {@code withPlugin} is what makes both orders behave identically; an {@code if} on
     * {@code hasPlugin} would only work for the case above.
     */
    @Test
    void addsSourceReportWhenJavaIsAppliedLast() {
        Project javaProject = ProjectBuilder.builder().withName("java-last").build();
        javaProject.getPluginManager().apply(MyPlugin.class);
        javaProject.getPluginManager().apply("java");

        assertNotNull(javaProject.getTasks().findByName("sourcereport"));
    }

    @Test
    void hooksSourceReportIntoCheck() {
        Project javaProject = ProjectBuilder.builder().withName("checked").build();
        javaProject.getPluginManager().apply("java");
        javaProject.getPluginManager().apply(MyPlugin.class);

        Task check = javaProject.getTasks().getByName("check");
        assertTrue(check.getTaskDependencies().getDependencies(check).stream()
                .anyMatch(task -> task.getName().equals("sourcereport")));
    }

    @Test
    void bundleDependsOnItsProducersThroughItsInputs() {
        Task bundle = project.getTasks().getByName("bundle");

        assertTrue(bundle.getTaskDependencies().getDependencies(bundle).stream()
                .map(Task::getName)
                .toList()
                .containsAll(List.of("mytask", "myothertask")));
    }
}
