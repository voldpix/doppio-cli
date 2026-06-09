package dev.voldpix.doppio.body;

import dev.voldpix.doppio.model.BodyBlock;
import dev.voldpix.doppio.model.BodyKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BodyProcessorTest {
    private final BodyProcessor processor = new BodyProcessor();

    @Test
    void validateJsonAndSetContentType() throws Exception {
        var body = """
            {
              "name": "voldpix",
              "active": true,
              "roles": ["admin"]
            }
            """;

        var result = processor.process(new BodyBlock(BodyKind.JSON, body));

        assertThat(result.content()).isEqualTo(body);
        assertThat(result.contentType()).isEqualTo("application/json");
    }

    @Test
    void processTextAndCsvBodies() throws Exception {
        assertThat(processor.process(new BodyBlock(BodyKind.TEXT, "hello")).contentType())
            .isEqualTo("text/plain; charset=utf-8");
        assertThat(processor.process(new BodyBlock(BodyKind.CSV, "a,b")).contentType())
            .isEqualTo("text/csv; charset=utf-8");
    }

    @Test
    void encodeFormBody() throws Exception {
        var result = processor.process(new BodyBlock(BodyKind.FORM, """
            username=voldpix
            role=admin user
            # comment
            empty=
            """));

        assertThat(result.content()).isEqualTo("username=voldpix&role=admin+user&empty=");
        assertThat(result.contentType()).isEqualTo("application/x-www-form-urlencoded");
    }

    @Test
    void returnEmptyProcessedBodyForMissingBody() throws Exception {
        var result = processor.process(null);

        assertThat(result.content()).isNull();
        assertThat(result.contentType()).isNull();
    }

    @Test
    void rejectInvalidJsonAndInvalidForm() {
        assertThatThrownBy(() -> processor.process(new BodyBlock(BodyKind.JSON, "{\"name\": }")))
            .isInstanceOf(BodyException.class)
            .hasMessageContaining("expected JSON value");

        assertThatThrownBy(() -> processor.process(new BodyBlock(BodyKind.FORM, "broken")))
            .isInstanceOf(BodyException.class)
            .hasMessageContaining("expected key=value");
    }
}
