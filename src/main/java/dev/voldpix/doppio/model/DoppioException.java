package dev.voldpix.doppio.model;

public class DoppioException extends Exception {
    private final ErrorKind kind;

    public DoppioException(ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public DoppioException(ErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public ErrorKind kind() {
        return kind;
    }
}
