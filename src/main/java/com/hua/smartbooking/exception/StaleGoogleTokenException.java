package com.hua.smartbooking.exception;

public class StaleGoogleTokenException extends RuntimeException {

    private final String userEmail;

    public StaleGoogleTokenException(String message, String userEmail) {
        super(message);
        this.userEmail = userEmail;
    }

    public String getUserEmail() {
        return userEmail;
    }
}