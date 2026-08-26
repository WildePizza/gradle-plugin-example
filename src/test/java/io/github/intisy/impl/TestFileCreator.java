package io.github.intisy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFileCreator {
    @TempDir
    Path tempDir;

    @Test
    void createsFileWithContent() throws IOException {
        File target = tempDir.resolve("out.txt").toFile();

        new FileCreator(target, "HELLO FROM MY PLUGIN").create();

        assertTrue(target.exists());
        assertEquals("HELLO FROM MY PLUGIN", Files.readString(target.toPath()));
    }

    @Test
    void createsMissingParentDirectories() throws IOException {
        File target = tempDir.resolve("deeply/nested/out.txt").toFile();

        new FileCreator(target, "HELLO FROM MY PLUGIN").create();

        assertTrue(target.exists());
        assertEquals("HELLO FROM MY PLUGIN", Files.readString(target.toPath()));
    }

    @Test
    void overwritesAnExistingFile() throws IOException {
        File target = tempDir.resolve("out.txt").toFile();
        Files.writeString(target.toPath(), "STALE");

        new FileCreator(target, "FRESH").create();

        assertEquals("FRESH", Files.readString(target.toPath()));
    }

    @Test
    void writesContentAsUtf8() throws IOException {
        File target = tempDir.resolve("out.txt").toFile();

        new FileCreator(target, "¯\\_(ツ)_/¯").create();

        assertEquals("¯\\_(ツ)_/¯", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
    }
}
