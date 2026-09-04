package com.hua.smartbooking.dto;

import com.hua.smartbooking.enums.RsvpStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RsvpRequest {
    @NotNull(message = "RSVP status is required")
    private RsvpStatus status;
}