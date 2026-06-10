package dev.voldpix.doppio.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedFileLoaderTest {
    @TempDir
    Path tempDir;

    private final SeedFileLoader loader = new SeedFileLoader();

    @Test
    void defaultSeedCanReferenceValuesFromSameFile() throws Exception {
        var seed = tempDir.resolve("default.seed");
        Files.writeString(seed, """
            BASE_URL=https://{{HOST}}/api
            USER_ID={{ID}}
            HOST=default.example.com
            ID=42
            """);

        assertThat(loader.loadResolvedIfExists(seed))
            .containsEntry("BASE_URL", "https://default.example.com/api")
            .containsEntry("USER_ID", "42")
            .containsEntry("HOST", "default.example.com")
            .containsEntry("ID", "42");
    }

    @Test
    void selectedSeedCanReferenceDefaultAndOwnValues() throws Exception {
        var defaultSeed = tempDir.resolve("default.seed");
        var devSeed = tempDir.resolve("dev.seed");
        Files.writeString(defaultSeed, """
            HOST=default.example.com
            TOKEN_PREFIX=base
            BASE_URL=https://{{HOST}}
            """);
        Files.writeString(devSeed, """
            HOST=dev.example.com
            BASE_URL=https://{{HOST}}/v2
            TOKEN={{TOKEN_PREFIX}}-{{HOST}}
            """);

        var defaultValues = loader.loadResolvedIfExists(defaultSeed);

        assertThat(loader.loadResolvedIfExists(devSeed, defaultValues))
            .containsEntry("HOST", "dev.example.com")
            .containsEntry("BASE_URL", "https://dev.example.com/v2")
            .containsEntry("TOKEN", "base-dev.example.com")
            .containsEntry("TOKEN_PREFIX", "base");
    }

    @Test
    void missingSeedReferenceReportsFileKeyAndReference() throws Exception {
        var seed = tempDir.resolve("default.seed");
        Files.writeString(seed, "BASE_URL=https://{{HOST}}");

        assertThatThrownBy(() -> loader.loadResolvedIfExists(seed))
            .isInstanceOf(SeedParseException.class)
            .hasMessageContaining(seed.toString())
            .hasMessageContaining("BASE_URL references HOST");
    }

    @Test
    void cyclicSeedReferencesFailClearly() throws Exception {
        var seed = tempDir.resolve("default.seed");
        Files.writeString(seed, """
            A={{B}}
            B={{A}}
            """);

        assertThatThrownBy(() -> loader.loadResolvedIfExists(seed))
            .isInstanceOf(SeedParseException.class)
            .hasMessageContaining("Cyclic seed variable reference")
            .hasMessageContaining("A -> B -> A");
    }
}
