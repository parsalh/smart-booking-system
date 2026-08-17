package com.hua.smartbooking.dto;

import lombok.Data;
import java.util.List;

@Data
public class RoomSuggestionRequest {
    private String startTime;
    private String endTime;
    private Integer minCapacity;
    private List<String> requiredAmenities;
}
