package dev.voldpix.doppio.shell;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShellStylerTest {
    @Test
    void colorsPromptWhenAnsiIsEnabled() {
        assertThat(new ShellStyler(true).prompt("dev"))
            .contains("\u001B[")
            .contains("doppio")
            .contains("[dev]");
    }

    @Test
    void leavesPromptPlainWhenAnsiIsDisabled() {
        assertThat(new ShellStyler(false).prompt("default")).isEqualTo("doppio:[default]> ");
    }
}
