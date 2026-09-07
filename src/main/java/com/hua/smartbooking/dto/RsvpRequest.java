package com.hua.smartbooking.dto;

import com.hua.smartbooking.enums.RsvpStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for POST /api/bookings/{bookingId}/rsvp: the new
 * RSVP status the current user wants to set for that booking.
 *
 * @author Stavroula Parsali
 */
@Data
public class RsvpRequest {
    @NotNull(message = "RSVP status is required")
    private RsvpStatus status;
}