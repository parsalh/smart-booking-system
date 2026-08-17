package com.hua.smartbooking.dto;

import lombok.Data;
import java.util.List;

@Data
public class RoomDTO {
    private Long id;
    private String name;
    private String building;
    private String location;
    private String floor;
    private Integer capacity;
    private String imageUrl;
    private List<String> amenities;
}
