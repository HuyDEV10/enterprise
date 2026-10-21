package com.huy.enterprise.ai;

public class AiServiceUnavailableException extends RuntimeException {
    private final String code;

    public AiServiceUnavailableException(String message) {
        this("AI_UNAVAILABLE", message);
    }

    public AiServiceUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.code = "AI_UNREACHABLE";
    }

    public String getCode() { return code; }
}
