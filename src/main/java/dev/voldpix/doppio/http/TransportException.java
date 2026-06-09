package dev.voldpix.doppio.http;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

public class TransportException extends DoppioException {
    public TransportException(String message, Throwable cause) {
        super(ErrorKind.NETWORK, message, cause);
    }
}
