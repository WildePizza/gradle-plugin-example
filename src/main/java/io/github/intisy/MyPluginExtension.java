package io.github.intisy;

import org.gradle.api.provider.Property;

/**
 * Configuration users declare in a {@code myplugin { }} block.
 *
 * @implNote The value is a {@link Property} rather than a plain field so tasks can be wired to it at
 * configuration time and still observe whatever the build script sets afterwards.
 */
public abstract class MyPluginExtension {
    public static final String NAME = "myplugin";

    public static final String DEFAULT_FILE_CONTENT = "¯\\_(ツ)_/¯";

    public abstract Property<String> getFileContent();

    public MyPluginExtension() {
        getFileContent().convention(DEFAULT_FILE_CONTENT);
    }
}
