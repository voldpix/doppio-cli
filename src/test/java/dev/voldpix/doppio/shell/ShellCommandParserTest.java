package dev.voldpix.doppio.shell;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShellCommandParserTest {
    private final ShellCommandParser parser = new ShellCommandParser();

    @Test
    void parsesQuotedArguments() throws Exception {
        assertThat(parser.parse("gen auth/login -H 'X Client=doppio shell' --body \"text\""))
            .containsExactly("gen", "auth/login", "-H", "X Client=doppio shell", "--body", "text");
    }

    @Test
    void preservesBackslashesUsedAsPathSeparators() throws Exception {
        assertThat(parser.parse("run auth\\login"))
            .containsExactly("run", "auth\\login");
    }

    @Test
    void stillAllowsEscapedWhitespaceOutsideQuotes() throws Exception {
        assertThat(parser.parse("run auth\\ login"))
            .containsExactly("run", "auth login");
    }

    @Test
    void rejectsUnterminatedQuotes() {
        assertThatThrownBy(() -> parser.parse("run 'auth/login"))
            .hasMessageContaining("Unterminated quote");
    }
}
