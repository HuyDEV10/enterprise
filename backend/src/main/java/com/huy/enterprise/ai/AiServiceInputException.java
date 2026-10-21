package com.huy.enterprise.ai;

import lombok.Getter;

@Getter
public class AiServiceInputException extends RuntimeException {
    private final String code;

    public AiServiceInputException(String code, String message) {
        super(message);
        this.code = code;
    }
}
