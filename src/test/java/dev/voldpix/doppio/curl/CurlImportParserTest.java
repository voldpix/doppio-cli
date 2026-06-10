package dev.voldpix.doppio.curl;

import dev.voldpix.doppio.model.HttpMethod;
import dev.voldpix.doppio.request.GeneratedBodyKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurlImportParserTest {
    private final CurlImportParser parser = new CurlImportParser();

    @Test
    void parsesPostJsonCurl() throws Exception {
        var result = parser.parse("""
            curl --location --request POST 'https://api.example.com/auth/login?source=docs&debug' \
              --header 'Content-Type: application/json' \
              --header 'Authorization: Bearer token' \
              --data-raw '{"email":"me@example.com"}'
            """);

        assertThat(result.method()).isEqualTo(HttpMethod.POST);
        assertThat(result.url()).isEqualTo("https://api.example.com/auth/login");
        assertThat(result.headers())
            .containsExactly("Content-Type=application/json", "Authorization=Bearer token");
        assertThat(result.queryParams()).containsExactly("source=docs", "debug");
        assertThat(result.bodyKind()).isEqualTo(GeneratedBodyKind.JSON);
        assertThat(result.body()).isEqualTo("{\"email\":\"me@example.com\"}");
    }

    @Test
    void defaultsToGetWhenCurlHasNoBody() throws Exception {
        var result = parser.parse("curl --url https://api.example.com/users/me -H 'Accept: application/json'");

        assertThat(result.method()).isEqualTo(HttpMethod.GET);
        assertThat(result.url()).isEqualTo("https://api.example.com/users/me");
        assertThat(result.headers()).containsExactly("Accept=application/json");
        assertThat(result.bodyKind()).isEqualTo(GeneratedBodyKind.NONE);
    }

    @Test
    void parsesMultipleDataBlocksAsFormBody() throws Exception {
        var result = parser.parse("""
            curl https://api.example.com/auth/login \
              -H 'Accept: application/json' \
              -d username=alice \
              -d password=secret
            """);

        assertThat(result.method()).isEqualTo(HttpMethod.POST);
        assertThat(result.bodyKind()).isEqualTo(GeneratedBodyKind.FORM);
        assertThat(result.body()).isEqualTo("""
            username=alice
            password=secret""");
    }

    @Test
    void rejectsUnsupportedCurlOptions() {
        assertThatThrownBy(() -> parser.parse("curl -I https://api.example.com"))
            .hasMessageContaining("Unsupported curl option: -I");
    }
}
