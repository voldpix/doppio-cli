package dev.voldpix.doppio.dsl;

import dev.voldpix.doppio.model.Header;
import dev.voldpix.doppio.model.HttpMethod;
import dev.voldpix.doppio.model.QueryParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DslProcessorTest {
    private DslProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DslProcessor();
    }

    @Test
    void parseMinimalRequest() throws Exception {
        var result = processor.process("GET https://example.com");

        assertThat(result.method()).isEqualTo(HttpMethod.GET);
        assertThat(result.url()).isEqualTo("https://example.com");
        assertThat(result.headers()).isEmpty();
        assertThat(result.queryParams()).isEmpty();
        assertThat(result.body()).isNull();
    }

    @Test
    void parseAllHttpMethods() throws Exception {
        assertThat(processor.process("GET https://example.com").method()).isEqualTo(HttpMethod.GET);
        assertThat(processor.process("POST https://example.com").method()).isEqualTo(HttpMethod.POST);
        assertThat(processor.process("PUT https://example.com").method()).isEqualTo(HttpMethod.PUT);
        assertThat(processor.process("PATCH https://example.com").method()).isEqualTo(HttpMethod.PATCH);
        assertThat(processor.process("DELETE https://example.com").method()).isEqualTo(HttpMethod.DELETE);
    }

    @Test
    void parseHeaders() throws Exception {
        var input = """
            POST https://example.com
            -h content-type=application/json
            header authorization=Bearer token123
            """;

        var result = processor.process(input);

        assertThat(result.headers())
            .containsExactlyInAnyOrder(
                new Header("content-type", "application/json"),
                new Header("authorization", "Bearer token123")
            );
    }

    @Test
    void parseQueryParams() throws Exception {
        var input = """
            GET https://example.com
            -q page=1
            query size=20
            -q filter=
            -q flag
            """;

        var result = processor.process(input);

        assertThat(result.queryParams())
            .containsExactly(
                new QueryParam("page", "1"),
                new QueryParam("size", "20"),
                new QueryParam("filter", ""),
                new QueryParam("flag", "")
            );
    }

    @Test
    void parseBody() throws Exception {
        var input = """
            POST https://example.com
            <|
            {"name": "John", "age": 30}
            |>
            """;

        var result = processor.process(input);

        assertThat(result.body()).isEqualTo("{\"name\": \"John\", \"age\": 30}");
    }

    @Test
    void preserveWhitespaceInsideBody() throws Exception {
        var input = """
            POST https://example.com
            <|
            {
              "name": "   lots   of   spaces   "
            }
            |>
            """;

        var result = processor.process(input);

        assertThat(result.body()).contains("\"name\": \"   lots   of   spaces   \"");
    }

    @Test
    void parseBodyBeforeRequestLine() throws Exception {
        var input = """
            <|
            {"id": 1}
            |>
            PUT https://example.com/users/1
            -h content-type=application/json
            """;

        var result = processor.process(input);

        assertThat(result.method()).isEqualTo(HttpMethod.PUT);
        assertThat(result.url()).isEqualTo("https://example.com/users/1");
        assertThat(result.body()).isEqualTo("{\"id\": 1}");
    }

    @Test
    void ignoreCommentsAndExtraWhitespaceOutsideBody() throws Exception {
        var input = """
            # this is a comment
            -- this is also ignored for compatibility
            GET    https://example.com
            -h   content-type=application/json
            """;

        var result = processor.process(input);

        assertThat(result.url()).isEqualTo("https://example.com");
        assertThat(result.headers()).containsExactly(new Header("content-type", "application/json"));
    }

    @Test
    void throwOnEmptyContent() {
        assertThatThrownBy(() -> processor.process("   \n  \n  "))
            .isInstanceOf(DslParseException.class)
            .hasMessageContaining("parse");
    }

    @Test
    void throwOnInvalidDirectives() {
        var input = """
            FETCH api.example.com
            -h InvalidHeaderNoEquals
            -q
            """;

        assertThatThrownBy(() -> processor.process(input))
            .isInstanceOf(DslParseException.class)
            .satisfies(error -> assertThat(((DslParseException) error).errors()).hasSize(3));
    }

    @Test
    void throwOnMalformedBodyDelimiters() {
        assertThatThrownBy(() -> processor.process("""
            POST https://example.com
            <|
            {"name": "alice"}
            """))
            .isInstanceOf(DslParseException.class);

        assertThatThrownBy(() -> processor.process("""
            POST https://example.com
            |>
            """))
            .isInstanceOf(DslParseException.class);

        assertThatThrownBy(() -> processor.process("""
            POST https://example.com
            <|
            body
            |>
            |>
            """))
            .isInstanceOf(DslParseException.class);
    }
}
