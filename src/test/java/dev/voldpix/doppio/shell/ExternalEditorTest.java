package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.model.DoppioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalEditorTest {
    @TempDir
    Path tempDir;

    private final ExternalEditor editor = new ExternalEditor();

    @Test
    void resolvesDoppioEditorBeforeConfigAndShellEditors() {
        var resolved = editor.resolve(
            Map.of(
                "DOPPIO_EDITOR", "zed --wait",
                "VISUAL", "code -w",
                "EDITOR", "nano",
                "PATH", ""
            ),
            new DoppioUserConfig("vim")
        );

        assertThat(resolved).hasValue(new ResolvedEditor("zed --wait", "DOPPIO_EDITOR"));
    }

    @Test
    void resolvesConfigBeforeVisualAndEditor() {
        var resolved = editor.resolve(
            Map.of("VISUAL", "code -w", "EDITOR", "nano", "PATH", ""),
            new DoppioUserConfig("vim")
        );

        assertThat(resolved).hasValue(new ResolvedEditor("vim", "~/.config/doppio/config.json"));
    }

    @Test
    void reportsHelpfulErrorWhenNoEditorCanBeResolved() {
        assertThatThrownBy(() -> editor.open(tempDir.resolve("request.dopo"), Map.of("PATH", ""), DoppioUserConfig.empty()))
            .isInstanceOf(DoppioException.class)
            .hasMessageContaining("editor use nano")
            .hasMessageContaining("DOPPIO_EDITOR");
    }
}
