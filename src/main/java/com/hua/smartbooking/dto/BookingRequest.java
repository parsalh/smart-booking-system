package com.hua.smartbooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Request body for POST /api/bookings/suggest-times: the meeting's
 * required and optional participants, desired duration, the date
 * range to search within, an optional daily time window, and how
 * many candidate slots to return.
 *
 * @author Stavroula Parsali
 */
@Data
public class BookingRequest {

    private List<String> requiredParticipants;
    private List<String> optionalParticipants;
    @Min(value = 15, message = "Duration must be at least 15 minutes")
    private int durationMinutes;

    @NotBlank(message = "Start date range is required")
    private String dateRangeStart;

    @NotBlank(message = "End date range is required")
    private String dateRangeEnd;
    private String dailyStartTime;
    private String dailyEndTime;

    private Integer maxResults;

}
