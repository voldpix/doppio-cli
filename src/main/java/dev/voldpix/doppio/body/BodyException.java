package dev.voldpix.doppio.body;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

public class BodyException extends DoppioException {
    public BodyException(String message) {
        super(ErrorKind.BODY, message);
    }
}
