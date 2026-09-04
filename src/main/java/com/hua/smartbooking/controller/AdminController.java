package com.hua.smartbooking.controller;

import com.hua.smartbooking.dto.RoleUpdateRequest;
import com.hua.smartbooking.enums.BookingStatus;
import com.hua.smartbooking.enums.Role;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.RoomRepository;
import com.hua.smartbooking.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public AdminController(RoomRepository roomRepository, UserRepository userRepository,
                           BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    private void addUserAttributes(Model model, OAuth2User principal) {
        if (principal != null) {
            String email = principal.getAttribute("email");
            User user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                model.addAttribute("name", user.getFullname());
                model.addAttribute("avatar", user.getAvatarUrl());
                model.addAttribute("role", user.getRole().name());
                model.addAttribute("email", user.getEmail());
            }
        }
    }

    @GetMapping("/rooms")
    public String listRooms(Model model, @AuthenticationPrincipal OAuth2User principal) {
        model.addAttribute("rooms", roomRepository.findAll());
        addUserAttributes(model, principal);
        return "admin-rooms";
    }

    @GetMapping("/bookings")
    public String listBookings(Model model, @AuthenticationPrincipal OAuth2User principal) {
        List<Booking> bookings = bookingRepository.findByStatusNotOrderByStartTimeDesc(BookingStatus.CANCELLED);
        model.addAttribute("bookings", bookings);
        addUserAttributes(model, principal);
        return "admin-bookings";
    }

    @GetMapping("/users")
    public String listUsers(Model model, @AuthenticationPrincipal OAuth2User principal) {
        model.addAttribute("users", userRepository.findAll());
        addUserAttributes(model, principal);
        return "admin-users";
    }

    @PostMapping("/users/{id}/role")
    @ResponseBody
    public ResponseEntity<?> updateUserRole(@PathVariable Long id,
                                            @Valid @RequestBody RoleUpdateRequest request,
                                            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String callerEmail = principal.getAttribute("email");
        User caller = userRepository.findByEmail(callerEmail).orElse(null);

        if (caller == null || caller.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins can change user roles"));
        }

        Optional<User> targetOpt = userRepository.findById(id);
        if (targetOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User target = targetOpt.get();

        if (target.getId().equals(caller.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot change your own role"));
        }

        Role newRole;
        try {
            newRole = Role.valueOf(request.getRole());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
        }

        target.setRole(newRole);
        userRepository.save(target);

        Map<String, String> body = new HashMap<>();
        body.put("message", "Role updated");
        body.put("role", newRole.name());
        return ResponseEntity.ok().body(body);
    }

    @PostMapping("/rooms/update")
    public String updateRoom(@ModelAttribute Room updatedRoom) {
        Optional<Room> existingRoomOpt = roomRepository.findById(updatedRoom.getId());

        if (existingRoomOpt.isPresent()) {
            Room existingRoom = existingRoomOpt.get();

            existingRoom.setName(updatedRoom.getName());
            existingRoom.setBuilding(updatedRoom.getBuilding());
            existingRoom.setLocation(updatedRoom.getLocation());
            existingRoom.setFloor(updatedRoom.getFloor());
            existingRoom.setCapacity(updatedRoom.getCapacity());
            existingRoom.setImageUrl(updatedRoom.getImageUrl());
            existingRoom.setIsAvailable(updatedRoom.getIsAvailable() != null && updatedRoom.getIsAvailable());

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
    public String deleteRoom(@RequestParam Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            roomRepository.deleteById(id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "This room cannot be deleted because it already has synced bookings.");
        }
        return "redirect:/admin/rooms";
    }


}