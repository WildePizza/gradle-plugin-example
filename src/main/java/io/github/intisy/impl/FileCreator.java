package io.github.intisy.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a file, knowing nothing about Gradle.
 *
 * @implNote Keeping the work in a plain class is what makes it unit testable; a {@code DefaultTask}
 * subclass needs a whole {@code Project} to construct. See tutorial 4.
 */
public class FileCreator {
    private final File outputFile;
    private final String content;

    public FileCreator(File outputFile, String content) {
        this.outputFile = outputFile;
        this.content = content;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public String getContent() {
        return content;
    }

    public void create() throws IOException {
        Path target = outputFile.toPath();
        Files.createDirectories(target.getParent());
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
    }
}
