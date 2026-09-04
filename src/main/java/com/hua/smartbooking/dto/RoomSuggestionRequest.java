package com.hua.smartbooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class RoomSuggestionRequest {
    @NotBlank(message = "Start time is required")
    private String startTime;

    @NotBlank(message = "End time is required")
    private String endTime;

    @NotNull(message = "Minimum capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer minCapacity;
    private List<String> requiredAmenities;
}
