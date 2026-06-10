package dev.voldpix.doppio.format;

import dev.voldpix.doppio.model.DoppioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DopoSourceFormatterTest {
    private final DopoSourceFormatter formatter = new DopoSourceFormatter();

    @Test
    void formatsJsonBodyAndPreservesCommentPosition() throws Exception {
        var input = """
            @name   Login    user
            POST    https://example.com/auth/login
            header    Content-Type=application/json
            query   source=doppio

            <JSON|

            {
            # "username":"{{USERNAME}}",
            "password":"{{PASSWORD}}",
            "profile":{"roles":["admin","user"],"enabled":true}
            }
            |>
            """;

        assertThat(formatter.format(input)).isEqualTo("""
            @name Login user
            POST https://example.com/auth/login
            -h Content-Type=application/json
            -q source=doppio

            <json|
            {
              # "username":"{{USERNAME}}",
              "password": "{{PASSWORD}}",
              "profile": {
                "roles": [
                  "admin",
                  "user"
                ],
                "enabled": true
              }
            }
            |>
            """);
    }

    @Test
    void preservesCommentsBetweenJsonEntries() throws Exception {
        var input = """
            POST https://example.com
            <json|
            {"password":"{{PASSWORD}}",
            # "username":"{{USERNAME}}",
            "remember":true}
            |>
            """;

        assertThat(formatter.format(input)).isEqualTo("""
            POST https://example.com

            <json|
            {
              "password": "{{PASSWORD}}",
              # "username":"{{USERNAME}}",
              "remember": true
            }
            |>
            """);
    }

    @Test
    void rejectsInlineJsonComments() {
        var input = """
            POST https://example.com
            <json|
            {"username": "{{USERNAME}}" # inline comment}
            |>
            """;

        assertThatThrownBy(() -> formatter.format(input))
            .isInstanceOf(DoppioException.class)
            .hasMessageContaining("Inline JSON comments");
    }

    @Test
    void formatsFormTextAndCsvBodiesConservatively() throws Exception {
        assertThat(formatter.format("""
            POST https://example.com
            <form|

              # disabled while testing
              username = {{USERNAME}}
              password = {{PASSWORD}}

            |>
            """)).isEqualTo("""
            POST https://example.com

            <form|
            # disabled while testing
            username={{USERNAME}}
            password={{PASSWORD}}
            |>
            """);

        assertThat(formatter.format("""
            POST https://example.com
            <text|

              hello  \s

            |>
            """)).isEqualTo("""
            POST https://example.com

            <text|
            hello
            |>
            """);

        assertThat(formatter.format("""
            POST https://example.com
            <csv|
            name,value  \s
            user,42
            |>
            """)).isEqualTo("""
            POST https://example.com

            <csv|
            name,value
            user,42
            |>
            """);
    }
}
