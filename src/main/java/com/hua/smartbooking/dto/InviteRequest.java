package com.hua.smartbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/invite/send: the email address of the
 * unregistered person to invite to SmartBooking.
 *
 * @author Stavroula Parsali
 */
@Data
public class InviteRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

}
