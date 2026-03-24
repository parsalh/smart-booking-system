package com.hua.smartbooking.mapper;

import com.hua.smartbooking.model.Room;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {

    public Room mapLocationToEntity(String locationName) {
        if (locationName == null || locationName.isEmpty()) {
            return null;
        }

        String cleanName = locationName.trim().toLowerCase();

        Room room = new Room();
        room.setName(cleanName);
        room.setIsAvailable(true);
        room.setLocation("Auto-generated");

        room.setFloor(extractFloor(cleanName));

        return room;
    }

    private String extractFloor(String location) {
        if (location == null) return "Unknown";
        String lowerLoc = location.toLowerCase();

        if (location.matches(".*\\b1\\.\\d+.*")) return "1st Floor";
        if (location.matches(".*\\b2\\.\\d+.*")) return "2nd Floor";
        if (location.matches(".*\\b3\\.\\d+.*")) return "3rd Floor";
        if (location.matches(".*\\b4\\.\\d+.*")) return "4th Floor";
        if (lowerLoc.contains("ground")) return "Ground Floor";
        if (lowerLoc.contains("basement")) return "Basement";

        return "TBD";
    }

}
