package dev.voldpix.doppio.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShellRequestResolverTest {
    @TempDir
    Path tempDir;

    private final ShellRequestResolver resolver = new ShellRequestResolver();

    @Test
    void resolvesExactNestedPathAndUniqueStem() throws Exception {
        request("auth/login.dopo", "@name Auth login\nGET https://example.com/login");
        request("users/me.dopo", "@name Current user\nGET https://example.com/me");

        assertThat(resolver.resolve("auth/login", tempDir).matches())
            .extracting(candidate -> candidate.relativePath().toString())
            .containsExactly("auth/login.dopo");
        assertThat(resolver.resolve("me", tempDir).matches())
            .extracting(candidate -> candidate.relativePath().toString())
            .containsExactly("users/me.dopo");
    }

    @Test
    void reportsAmbiguousStemMatches() throws Exception {
        request("auth/login.dopo", "@name Auth login\nGET https://example.com/login");
        request("admin/login.dopo", "@name Admin login\nGET https://example.com/admin/login");

        var lookup = resolver.resolve("login", tempDir);

        assertThat(lookup.ambiguous()).isTrue();
        assertThat(lookup.matches())
            .extracting(candidate -> candidate.relativePath().toString())
            .containsExactly("admin/login.dopo", "auth/login.dopo");
    }

    @Test
    void supportsCaseInsensitiveStemMatchAndNoMatch() throws Exception {
        request("auth/login.dopo", "@name Auth login\nGET https://example.com/login");

        assertThat(resolver.resolve("LOGIN", tempDir).matches())
            .extracting(candidate -> candidate.relativePath().toString())
            .containsExactly("auth/login.dopo");
        assertThat(resolver.resolve("missing", tempDir).found()).isFalse();
    }

    private void request(String relativePath, String content) throws Exception {
        var path = tempDir.resolve(".doppio/requests").resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "");
    }
}
