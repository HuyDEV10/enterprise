package com.huy.enterprise.ai.external;

public class GdeltUnavailableException extends RuntimeException {
    public GdeltUnavailableException(String message) { super(message); }
    public GdeltUnavailableException(String message, Throwable cause) { super(message, cause); }
}
