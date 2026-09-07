package com.hua.smartbooking.exception;

/**
 * Thrown when a user's stored Google refresh token has become
 * invalid (e.g. revoked access), signalling that they need to sign
 * in with Google again before their calendar can be checked.
 *
 * @author Stavroula Parsali
 */
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