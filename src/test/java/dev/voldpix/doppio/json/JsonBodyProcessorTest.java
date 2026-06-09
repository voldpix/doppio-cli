package dev.voldpix.doppio.json;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonBodyProcessorTest {
    private final JsonBodyProcessor processor = new JsonBodyProcessor();

    @Test
    void validateAndPreserveJsonBody() throws Exception {
        var body = """
            {
              "name": "voldpix",
              "active": true,
              "roles": ["admin"]
            }
            """;

        assertThat(processor.process(body)).isEqualTo(body);
    }

    @Test
    void returnNullForMissingBody() throws Exception {
        assertThat(processor.process(null)).isNull();
        assertThat(processor.process("   ")).isNull();
    }

    @Test
    void rejectInvalidJson() {
        assertThatThrownBy(() -> processor.process("{\"name\": }"))
            .isInstanceOf(JsonBodyException.class)
            .hasMessageContaining("expected JSON value");
    }
}
