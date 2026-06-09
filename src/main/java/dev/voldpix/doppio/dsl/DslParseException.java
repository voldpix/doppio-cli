package dev.voldpix.doppio.dsl;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.util.List;

public class DslParseException extends DoppioException {
    private final List<ParseError> errors;

    public DslParseException(List<ParseError> errors) {
        super(ErrorKind.PARSE, "Failed to parse .dopo file");
        this.errors = List.copyOf(errors);
    }

    public List<ParseError> errors() {
        return errors;
    }
}
