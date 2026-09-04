package com.hua.smartbooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class RoomDTO {
    private Long id;

    @NotBlank(message = "Room name is required")
    private String name;

    private String building;
    private String location;
    private String floor;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    private String imageUrl;
    private List<String> amenities;
    private Boolean isAvailable;
}