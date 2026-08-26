package io.github.intisy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSourceReporter {
    @TempDir
    Path tempDir;

    private List<String> linesOf(String rendered) {
        return Arrays.asList(rendered.split("\\R"));
    }

    @Test
    void countsTheSources() {
        SourceReporter reporter = new SourceReporter(
                Arrays.asList(new File("Beta.java"), new File("Alpha.java")),
                tempDir.resolve("report.txt").toFile());

        assertEquals("2 source files", linesOf(reporter.render()).get(0));
    }

    @Test
    void sortsTheNamesSoTheOutputIsStable() {
        SourceReporter reporter = new SourceReporter(
                Arrays.asList(new File("Zulu.java"), new File("Alpha.java"), new File("Mike.java")),
                tempDir.resolve("report.txt").toFile());

        assertEquals(Arrays.asList("3 source files", "Alpha.java", "Mike.java", "Zulu.java"),
                linesOf(reporter.render()));
    }

    @Test
    void handlesNoSourcesAtAll() {
        SourceReporter reporter = new SourceReporter(
                Collections.emptyList(), tempDir.resolve("report.txt").toFile());

        assertEquals("0 source files", linesOf(reporter.render()).get(0));
    }

    @Test
    void writesTheReportToDisk() throws IOException {
        File target = tempDir.resolve("nested/report.txt").toFile();

        new SourceReporter(Collections.singletonList(new File("Alpha.java")), target).report();

        assertTrue(target.exists());
        assertTrue(Files.readString(target.toPath()).startsWith("1 source files"));
    }
}
