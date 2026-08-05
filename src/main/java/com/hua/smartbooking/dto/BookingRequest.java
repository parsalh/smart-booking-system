package com.hua.smartbooking.dto;

import lombok.Data;

import java.util.List;

@Data
public class BookingRequest {

    private List<String> requiredParticipants;
    private List<String> optionalParticipants;
    private int durationMinutes;
    private String dateRangeStart;
    private String dateRangeEnd;

}
