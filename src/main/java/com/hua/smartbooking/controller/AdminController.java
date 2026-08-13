package com.hua.smartbooking.controller;

import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public AdminController(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping("/rooms")
    public String listRooms(Model model) {
        model.addAttribute("rooms", roomRepository.findAll());
        return "admin-rooms";
    }

    @PostMapping("/rooms/update")
    public String updateRoom(@ModelAttribute Room updatedRoom) {
        Optional<Room> existingRoomOpt = roomRepository.findById(updatedRoom.getId());

        if (existingRoomOpt.isPresent()) {
            Room existingRoom = existingRoomOpt.get();

            existingRoom.setName(updatedRoom.getName());
            existingRoom.setLocation(updatedRoom.getLocation());
            existingRoom.setFloor(updatedRoom.getFloor());
            existingRoom.setCapacity(updatedRoom.getCapacity());
            existingRoom.setImageUrl(updatedRoom.getImageUrl());

            if (existingRoom.getAmenities() != null) {
                existingRoom.getAmenities().clear();
            } else {
                existingRoom.setAmenities(new ArrayList<>());
            }

            if (updatedRoom.getAmenities() != null && !updatedRoom.getAmenities().isEmpty()) {
                existingRoom.getAmenities().addAll(updatedRoom.getAmenities());
            }

            roomRepository.save(existingRoom);
        }

        return "redirect:/admin/rooms";
    }

    @PostMapping("/rooms/add")
    public String addRoom(@ModelAttribute Room room) {
        if (room.getName() != null) {
            room.setName(room.getName().trim());
        }
        room.setIsAvailable(true);
        roomRepository.save(room);
        return "redirect:/admin/rooms";
    }

    @PostMapping("/rooms/delete")
    public String deleteRoom(@RequestParam Long id) {
        roomRepository.deleteById(id);
        return "redirect:/admin/rooms";
    }


}
