package dev.voldpix.doppio.seed;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

public class SeedParseException extends DoppioException {
    public SeedParseException(String message) {
        super(ErrorKind.SEED, message);
    }
}
