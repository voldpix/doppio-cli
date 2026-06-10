package dev.voldpix.doppio.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DoppioConfigStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void writesReadsAndClearsEditorCommand() {
        var store = new DoppioConfigStore(tempDir.resolve("config"));

        store.write(new DoppioUserConfig("code -w"));

        assertThat(store.read().editorCommand()).isEqualTo("code -w");

        store.write(store.read().withEditorCommand(null));

        assertThat(store.read().editorCommand()).isNull();
    }
}
