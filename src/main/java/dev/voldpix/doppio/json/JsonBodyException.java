package dev.voldpix.doppio.json;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

public class JsonBodyException extends DoppioException {
    public JsonBodyException(String message) {
        super(ErrorKind.BODY, message);
    }
}
