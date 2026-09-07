package com.hua.smartbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * A room returned from POST /api/bookings/suggest-rooms, with its
 * basic details plus which of the requested amenities it's missing.
 *
 * @author Stavroula Parsali
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomSuggestionResult {
    private Long id;
    private String name;
    private String building;
    private String location;
    private Integer capacity;
    private String floor;
    private String imageUrl;
    private List<String> amenities;
    private List<String> missingAmenities;
}
