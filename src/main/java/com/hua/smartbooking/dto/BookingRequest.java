package com.hua.smartbooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

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
