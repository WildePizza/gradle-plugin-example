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
