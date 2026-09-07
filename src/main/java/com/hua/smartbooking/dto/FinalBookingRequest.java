package com.hua.smartbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request body for creating one or more actual bookings once a time
 * slot and room have been chosen: the room, meeting title, start/end
 * time, participant list, and optionally a number of weeks to repeat
 * the booking on the same weekday/time, with a flag to force booking
 * whichever weeks succeed even if some conflict.
 *
 * @author Stavroula Parsali
 */
@Data
public class FinalBookingRequest {

    @NotNull(message = "Room ID is required")
    private Long roomId;

    @NotBlank(message = "Meeting title is required")
    private String title;

    @NotBlank(message = "Start time is required")
    private String startTime;

    @NotBlank(message = "End time is required")
    private String endTime;
    private List<String> participants;

    private Integer repeatWeeks;
    private Boolean forcePartial;

}
