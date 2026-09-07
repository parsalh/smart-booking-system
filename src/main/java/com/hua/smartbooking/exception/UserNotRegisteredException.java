package com.hua.smartbooking.exception;

/**
 * Custom runtime exception thrown when the system encounters an email address
 * that does not exist in the local database or lacks an OAuth2 refresh token.
 *
 * @author Stavroula Parsali
 */
public class UserNotRegisteredException extends RuntimeException {

    private final String missingEmail;

    public UserNotRegisteredException(String message, String missingEmail) {
        super(message);
        this.missingEmail = missingEmail;
    }

    public String getMissingEmail() {
        return missingEmail;
    }

}
