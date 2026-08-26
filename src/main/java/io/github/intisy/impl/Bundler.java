package io.github.intisy.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Concatenates files into one, knowing nothing about Gradle.
 *
 * @implNote Sorted by name for the same reason {@link SourceReporter} sorts, so that identical
 * inputs always produce identical bytes.
 */
public class Bundler {
    private final Collection<File> sources;
    private final File outputFile;

    public Bundler(Collection<File> sources, File outputFile) {
        this.sources = sources;
        this.outputFile = outputFile;
    }

    public String render() throws IOException {
        List<File> ordered = new ArrayList<>(sources);
        ordered.sort(Comparator.comparing(File::getName));

        StringBuilder bundle = new StringBuilder();
        for (File source : ordered) {
            bundle.append("--- ").append(source.getName()).append(" ---").append(System.lineSeparator());
            bundle.append(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
            bundle.append(System.lineSeparator());
        }
        return bundle.toString();
    }

    public void bundle() throws IOException {
        Path target = outputFile.toPath();
        Files.createDirectories(target.getParent());
        Files.write(target, render().getBytes(StandardCharsets.UTF_8));
    }
}
