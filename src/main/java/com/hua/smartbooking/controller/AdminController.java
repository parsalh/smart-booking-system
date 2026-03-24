package com.hua.smartbooking.controller;

import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final RoomRepository roomRepository;

    public AdminController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @GetMapping("/rooms")
    public String listRooms(Model model) {
        model.addAttribute("rooms", roomRepository.findAll());
        return "admin-rooms";
    }

    @PostMapping("/rooms/update")
    public String updateRoom(@RequestParam Long id,
                             @RequestParam String floor,
                             @RequestParam Integer capacity,
                             @RequestParam(required = false) String location) {
        Room room = roomRepository.findById(id).orElseThrow();
        room.setFloor(floor);
        room.setCapacity(capacity);
        room.setLocation(location);
        roomRepository.save(room);
        return "redirect:/admin/rooms";
    }

    @PostMapping("/rooms/add")
    public String addRoom(@ModelAttribute Room room) {
        if (room.getName() != null) {
            room.setName(room.getName().trim().toLowerCase());
        }
        room.setIsAvailable(true);
        roomRepository.save(room);
        return "redirect:/admin/rooms";
    }


}
