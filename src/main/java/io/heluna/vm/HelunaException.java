package io.heluna.vm;

public class HelunaException extends RuntimeException {
    public HelunaException(String message) {
        super(message);
    }

    public HelunaException(String message, Throwable cause) {
        super(message, cause);
    }
}
