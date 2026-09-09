package com.hua.smartbooking.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/**
 * Data transfer object for creating and editing rooms in the admin
 * panel.
 *
 * @author Stavroula Parsali
 */
@Data
public class RoomDTO {
    private Long id;

    @NotBlank(message = "Room name is required")
    private String name;

    private String building;
    private String location;
    @Pattern(regexp = "^(-?[0-9]|10|-10)?$", message = "Floor must be a whole number from -10 to 10")
    private String floor;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 100, message = "Capacity must be 100 or less")
    private Integer capacity;

    private String imageUrl;
    private List<String> amenities;
    private Boolean isAvailable;
}