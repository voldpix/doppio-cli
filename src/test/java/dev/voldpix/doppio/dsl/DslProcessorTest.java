package dev.voldpix.doppio.dsl;

import dev.voldpix.doppio.expect.ExpectationKind;
import dev.voldpix.doppio.model.BodyKind;
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

        assertThat(result.name()).isNull();
        assertThat(result.method()).isEqualTo(HttpMethod.GET);
        assertThat(result.url()).isEqualTo("https://example.com");
        assertThat(result.headers()).isEmpty();
        assertThat(result.queryParams()).isEmpty();
        assertThat(result.body()).isNull();
    }

    @Test
    void parseNameAndLocalVariablesBeforeRequest() throws Exception {
        var input = """
            @name Login user
            @var TOKEN="local token"
            @var USER_ID=42
            POST https://example.com/users/42
            """;

        var metadata = processor.parseMetadata(input);
        var result = processor.process(input);

        assertThat(metadata.name()).isEqualTo("Login user");
        assertThat(metadata.variables())
            .containsEntry("TOKEN", "local token")
            .containsEntry("USER_ID", "42");
        assertThat(result.name()).isEqualTo("Login user");
    }

    @Test
    void parseExpectationsBeforeRequest() throws Exception {
        var input = """
            @name Login user
            @expect status=200
            @expect header Content-Type contains json
            @expect body contains "ok"
            GET https://example.com/users/me
            """;

        var metadata = processor.parseMetadata(input);
        var inspection = processor.processWithMetadata(input);

        assertThat(metadata.expectations())
            .extracting("kind")
            .containsExactly(ExpectationKind.STATUS, ExpectationKind.HEADER_CONTAINS, ExpectationKind.BODY_CONTAINS);
        assertThat(metadata.expectations())
            .extracting("target")
            .containsExactly("status", "Content-Type", "body");
        assertThat(metadata.expectations())
            .extracting("expected")
            .containsExactly("200", "json", "ok");
        assertThat(inspection.metadata().expectations()).hasSize(3);
    }

    @Test
    void inspectAllowsTemplatedUrlWithoutHydration() throws Exception {
        var input = """
            @name Login user
            @var EMAIL=user@example.com
            POST {{BASE_URL}}/auth/login
            -h Authorization=Bearer {{TOKEN}}
            -q source=doppio
            <json|
            {
              "email": "{{EMAIL}}"
            }
            |>
            """;

        var inspection = processor.inspect(input);

        assertThat(inspection.metadata().variables()).containsEntry("EMAIL", "user@example.com");
        assertThat(inspection.request().name()).isEqualTo("Login user");
        assertThat(inspection.request().method()).isEqualTo(HttpMethod.POST);
        assertThat(inspection.request().url()).isEqualTo("{{BASE_URL}}/auth/login");
        assertThat(inspection.request().headers()).containsExactly(new Header("Authorization", "Bearer {{TOKEN}}"));
        assertThat(inspection.request().queryParams()).containsExactly(new QueryParam("source", "doppio"));
        assertThat(inspection.request().body().kind()).isEqualTo(BodyKind.JSON);
    }

    @Test
    void rejectMetadataAfterRequestLine() {
        assertThatThrownBy(() -> processor.process("""
            GET https://example.com
            @name Too late
            """))
            .isInstanceOf(DslParseException.class)
            .satisfies(error -> assertThat(((DslParseException) error).errors().getFirst().hint())
                .contains("before the request line"));

        assertThatThrownBy(() -> processor.process("""
            GET https://example.com
            @expect status=200
            """))
            .isInstanceOf(DslParseException.class)
            .satisfies(error -> assertThat(((DslParseException) error).errors().getFirst().hint())
                .contains("before the request line"));
    }

    @Test
    void rejectDuplicateNameAndDuplicateLocalVariables() {
        assertThatThrownBy(() -> processor.parseMetadata("""
            @name One
            @name Two
            GET https://example.com
            """))
            .isInstanceOf(DslParseException.class)
            .satisfies(error -> assertThat(((DslParseException) error).errors().getFirst().hint())
                .contains("only one @name"));

        assertThatThrownBy(() -> processor.parseMetadata("""
            @var TOKEN=one
            @var TOKEN=two
            GET https://example.com
            """))
            .isInstanceOf(DslParseException.class)
            .satisfies(error -> assertThat(((DslParseException) error).errors().getFirst().hint())
                .contains("duplicate local variable"));
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
    void parseHeadersAndQueryParams() throws Exception {
        var input = """
            GET https://example.com
            -h content-type=application/json
            header authorization=Bearer token123
            -q page=1
            query size=20
            -q filter=
            -q flag
            """;

        var result = processor.process(input);

        assertThat(result.headers())
            .containsExactlyInAnyOrder(
                new Header("content-type", "application/json"),
                new Header("authorization", "Bearer token123")
            );
        assertThat(result.queryParams())
            .containsExactly(
                new QueryParam("page", "1"),
                new QueryParam("size", "20"),
                new QueryParam("filter", ""),
                new QueryParam("flag", "")
            );
    }

    @Test
    void parseDefaultAndExplicitTypedBodies() throws Exception {
        assertThat(processor.process("""
            POST https://example.com
            <|
            {"name": "John"}
            |>
            """).body().kind()).isEqualTo(BodyKind.JSON);

        assertThat(processor.process("""
            POST https://example.com
            <json|
            {"name": "John"}
            |>
            """).body().kind()).isEqualTo(BodyKind.JSON);

        assertThat(processor.process("""
            POST https://example.com
            <text|
            hello
            |>
            """).body().kind()).isEqualTo(BodyKind.TEXT);

        assertThat(processor.process("""
            POST https://example.com
            <csv|
            a,b
            |>
            """).body().kind()).isEqualTo(BodyKind.CSV);

        assertThat(processor.process("""
            POST https://example.com
            <form|
            username=voldpix
            |>
            """).body().kind()).isEqualTo(BodyKind.FORM);
    }

    @Test
    void preserveWhitespaceAndIgnoreHashCommentsInsideBody() throws Exception {
        var input = """
            POST https://example.com
            <json|
            # body comment
            {
              "name": "   lots   of   spaces   "
            }
            |>
            """;

        var result = processor.process(input);

        assertThat(result.body().content())
            .doesNotContain("# body comment")
            .contains("\"name\": \"   lots   of   spaces   \"");
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
        assertThat(result.body().content()).isEqualTo("{\"id\": 1}");
    }

    @Test
    void onlyHashStartsAComment() throws Exception {
        var result = processor.process("""
            # this is a comment
            GET    https://example.com
            -h   content-type=application/json
            """);

        assertThat(result.url()).isEqualTo("https://example.com");
        assertThat(result.headers()).containsExactly(new Header("content-type", "application/json"));

        assertThatThrownBy(() -> processor.process("""
            -- no longer a comment
            GET https://example.com
            """))
            .isInstanceOf(DslParseException.class);
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
    void throwOnInvalidExpectations() {
        assertThatThrownBy(() -> processor.process("""
            @expect status=ok
            GET https://example.com
            """))
            .isInstanceOf(DslParseException.class)
            .satisfies(error -> assertThat(((DslParseException) error).errors().getFirst().hint())
                .contains("numeric HTTP status"));

        assertThatThrownBy(() -> processor.process("""
            @expect header Content-Type equals json
            GET https://example.com
            """))
            .isInstanceOf(DslParseException.class)
            .satisfies(error -> assertThat(((DslParseException) error).errors().getFirst().hint())
                .contains("@expect status=200"));
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
