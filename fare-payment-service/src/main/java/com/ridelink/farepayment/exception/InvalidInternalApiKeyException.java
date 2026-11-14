package com.ridelink.farepayment.exception;

public class InvalidInternalApiKeyException extends RuntimeException {
    public InvalidInternalApiKeyException(String message) {
        super(message);
    }
}