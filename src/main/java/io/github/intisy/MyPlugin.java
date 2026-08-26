package io.github.intisy;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

public class MyPlugin implements Plugin<Project> {
    public static final String GROUP = "MyPlugin";

    @Override
    public void apply(Project project) {
        MyPluginExtension extension = project.getExtensions()
                .create(MyPluginExtension.NAME, MyPluginExtension.class);

        project.getTasks().register("dealwithit", task -> {
            task.setGroup(GROUP);
            task.setDescription("Print the plugin's greeting");
            task.doLast(action -> System.out.println("(•_•) ( •_•)>⌐■-■ (⌐■_■)"));
        });

        TaskProvider<MyTask> myTask = project.getTasks()
                .register("mytask", MyTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Create myfile.txt in the build directory");
                    task.getOutputFile().convention(
                            project.getLayout().getBuildDirectory().file("myfile.txt"));
                    task.getFileContent().convention(extension.getFileContent());
                });

        TaskProvider<MyTask> myOtherTask = project.getTasks()
                .register("myothertask", MyTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Create otherfile.txt in the build directory");
                    task.getOutputFile().convention(
                            project.getLayout().getBuildDirectory().file("otherfile.txt"));
                    task.getFileContent().convention(extension.getFileContent());
                });

        project.getTasks().register("mytestabletask", MyTestableTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Create testablefile.txt using the unit testable FileCreator");
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("testablefile.txt"));
            task.getFileContent().convention(extension.getFileContent());
        });

        project.getTasks().register("bundle", BundleTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Concatenate the other tasks' output into bundle.txt");
            task.getSources().from(
                    myTask.flatMap(MyTask::getOutputFile),
                    myOtherTask.flatMap(MyTask::getOutputFile));
            task.getOutputFile().convention(
                    project.getLayout().getBuildDirectory().file("bundle.txt"));
        });

        addJavaIntegration(project);
    }

    /**
     * Adds the parts of the plugin that only make sense alongside the {@code java} plugin.
     *
     * @implNote The callback reacts to {@code java} being applied rather than applying it. Applying
     * it here would force the Java plugin onto every consumer, and would still lose the race
     * whenever the build script applies {@code java} after this plugin.
     */
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
}
