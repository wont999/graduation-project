package com.example.common.exception;

public class RequestCapacityExceededException extends RuntimeException {
    public RequestCapacityExceededException(String message) {
        super(message);
    }
}
