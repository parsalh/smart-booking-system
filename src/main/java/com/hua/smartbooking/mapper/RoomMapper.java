package com.hua.smartbooking.mapper;

import com.hua.smartbooking.dto.RoomDTO;
import com.hua.smartbooking.model.Room;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {

    public RoomDTO toDTO(Room room) {
        if (room == null) return null;

        RoomDTO dto = new RoomDTO();
        dto.setId(room.getId());
        dto.setName(room.getName());
        dto.setBuilding(room.getBuilding());
        dto.setLocation(room.getLocation());
        dto.setFloor(room.getFloor());
        dto.setCapacity(room.getCapacity());
        dto.setImageUrl(room.getImageUrl());
        dto.setAmenities(room.getAmenities());

        return dto;
    }

}
