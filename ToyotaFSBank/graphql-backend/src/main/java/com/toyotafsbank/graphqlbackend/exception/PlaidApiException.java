package com.toyotafsbank.graphqlbackend.exception;

public class PlaidApiException extends RuntimeException {
    public PlaidApiException(String message) {
        super(message);
    }

    public PlaidApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
