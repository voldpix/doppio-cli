package dev.voldpix.doppio.seed;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class SeedFileLoader {
    private final SeedParser parser;
    private final SeedVariableResolver variableResolver;

    public SeedFileLoader() {
        this(new SeedParser(), new SeedVariableResolver());
    }

    public SeedFileLoader(SeedParser parser) {
        this(parser, new SeedVariableResolver());
    }

    public SeedFileLoader(SeedParser parser, SeedVariableResolver variableResolver) {
        this.parser = parser;
        this.variableResolver = variableResolver;
    }

    public Map<String, String> loadResolvedIfExists(Path seedFile) throws DoppioException {
        return loadResolvedIfExists(seedFile, Map.of());
    }

    public Map<String, String> loadResolvedIfExists(Path seedFile, Map<String, String> baseValues) throws DoppioException {
        if (!Files.exists(seedFile)) {
            return new LinkedHashMap<>(baseValues);
        }

        try {
            var rawValues = parser.parse(Files.readString(seedFile));
            return variableResolver.resolve(seedFile, baseValues, rawValues);
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.SEED, "Unable to read seed file: " + seedFile, e);
        }
    }
}
