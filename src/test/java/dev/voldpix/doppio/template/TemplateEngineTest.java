package dev.voldpix.doppio.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateEngineTest {
    private final TemplateEngine engine = new TemplateEngine();

    @Test
    void hydrateVariablesFromProvidedMap() throws Exception {
        var result = engine.hydrate(
            "GET {{ BASE_URL }}/users/{{USER_ID}} -h Authorization={{TOKEN}}",
            Map.of("BASE_URL", "https://seed.example.com", "TOKEN", "seed-token", "USER_ID", "42")
        );

        assertThat(result)
            .contains("https://seed.example.com")
            .contains("/users/42")
            .contains("seed-token");
    }

    @Test
    void replaceRepeatedVariablesInBody() throws Exception {
        var result = engine.hydrate(
            "{\"user\":\"{{USER}}\",\"copy\":\"{{ USER }}\"}",
            Map.of("USER", "voldpix")
        );

        assertThat(result).isEqualTo("{\"user\":\"voldpix\",\"copy\":\"voldpix\"}");
    }

    @Test
    void failOnMissingVariableBeforeRequestExecution() {
        assertThatThrownBy(() -> engine.hydrate("{{MISSING}}", Map.of()))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining("MISSING");
    }
}
