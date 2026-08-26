package io.github.intisy.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Summarises a set of source files, knowing nothing about Gradle.
 *
 * @implNote The file names are sorted before they are written. A file collection has no guaranteed
 * iteration order, so without this the report content would vary between runs and the task could
 * never be considered up to date.
 */
public class SourceReporter {
    private final Collection<File> sources;
    private final File outputFile;

    public SourceReporter(Collection<File> sources, File outputFile) {
        this.sources = sources;
        this.outputFile = outputFile;
    }

    public String render() {
        List<String> names = new ArrayList<>();
        for (File source : sources) {
            names.add(source.getName());
        }
        Collections.sort(names);

        StringBuilder report = new StringBuilder();
        report.append(names.size()).append(" source files").append(System.lineSeparator());
        for (String name : names) {
            report.append(name).append(System.lineSeparator());
        }
        return report.toString();
    }

    public void report() throws IOException {
        Path target = outputFile.toPath();
        Files.createDirectories(target.getParent());
        Files.write(target, render().getBytes(StandardCharsets.UTF_8));
    }
}
