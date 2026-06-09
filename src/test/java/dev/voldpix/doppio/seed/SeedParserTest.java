package dev.voldpix.doppio.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedParserTest {
    private final SeedParser parser = new SeedParser();

    @Test
    void parseSimpleDotenvSeed() throws Exception {
        var result = parser.parse("""
            # local values
            BASE_URL = https://example.com
            TOKEN="abc 123"
            USERNAME='voldpix'
            EMPTY=
            """);

        assertThat(result)
            .containsEntry("BASE_URL", "https://example.com")
            .containsEntry("TOKEN", "abc 123")
            .containsEntry("USERNAME", "voldpix")
            .containsEntry("EMPTY", "");
    }

    @Test
    void rejectMalformedLines() {
        assertThatThrownBy(() -> parser.parse("BROKEN"))
            .isInstanceOf(SeedParseException.class)
            .hasMessageContaining("expected KEY=value");
    }

    @Test
    void rejectDuplicateKeys() {
        assertThatThrownBy(() -> parser.parse("""
            TOKEN=one
            TOKEN=two
            """))
            .isInstanceOf(SeedParseException.class)
            .hasMessageContaining("Duplicate seed key");
    }
}
