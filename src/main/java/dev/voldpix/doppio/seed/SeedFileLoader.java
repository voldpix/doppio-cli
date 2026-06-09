package dev.voldpix.doppio.seed;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class SeedFileLoader {
    private final SeedParser parser;

    public SeedFileLoader() {
        this(new SeedParser());
    }

    public SeedFileLoader(SeedParser parser) {
        this.parser = parser;
    }

    public Map<String, String> loadIfExists(Path seedFile) throws DoppioException {
        if (!Files.exists(seedFile)) {
            return Map.of();
        }

        try {
            return parser.parse(Files.readString(seedFile));
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.SEED, "Unable to read seed file: " + seedFile, e);
        }
    }
}
