package io.github.intisy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBundler {
    @TempDir
    Path tempDir;

    private File write(String name, String content) throws IOException {
        Path target = tempDir.resolve(name);
        Files.writeString(target, content);
        return target.toFile();
    }

    @Test
    void concatenatesWithAHeaderPerFile() throws IOException {
        File alpha = write("alpha.txt", "AAA");

        String rendered = new Bundler(Collections.singletonList(alpha),
                tempDir.resolve("out.txt").toFile()).render();

        assertTrue(rendered.contains("--- alpha.txt ---"));
        assertTrue(rendered.contains("AAA"));
    }

    @Test
    void ordersByNameSoTheOutputIsStable() throws IOException {
        File zulu = write("zulu.txt", "ZZZ");
        File alpha = write("alpha.txt", "AAA");

        String rendered = new Bundler(Arrays.asList(zulu, alpha),
                tempDir.resolve("out.txt").toFile()).render();

        assertTrue(rendered.indexOf("alpha.txt") < rendered.indexOf("zulu.txt"));
    }

    @Test
    void handlesNoSourcesAtAll() throws IOException {
        assertEquals("", new Bundler(Collections.emptyList(),
                tempDir.resolve("out.txt").toFile()).render());
    }

    @Test
    void writesTheBundleToDisk() throws IOException {
        File alpha = write("alpha.txt", "AAA");
        File target = tempDir.resolve("nested/bundle.txt").toFile();

        new Bundler(Collections.singletonList(alpha), target).bundle();

        assertTrue(target.exists());
        assertTrue(Files.readString(target.toPath()).contains("AAA"));
    }
}
