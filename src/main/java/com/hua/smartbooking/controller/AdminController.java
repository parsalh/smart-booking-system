package com.hua.smartbooking.controller;

import com.hua.smartbooking.dto.RoleUpdateRequest;
import com.hua.smartbooking.dto.RoomDTO;
import com.hua.smartbooking.enums.Role;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.RoomRepository;
import com.hua.smartbooking.repository.UserRepository;
import com.hua.smartbooking.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "Admin - Users", description = "Admin-only: manage registered users' roles")
public class AdminController {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final FileStorageService fileStorageService;

    public AdminController(RoomRepository roomRepository, UserRepository userRepository,
                           BookingRepository bookingRepository, FileStorageService fileStorageService) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.fileStorageService = fileStorageService;
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
        List<Booking> bookings = bookingRepository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getStartTime).reversed())
                .collect(Collectors.toList());
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

    @Operation(
            summary = "Change a user's role",
            description = "Admin-only. Promotes or demotes another registered user to STUDENT, PROFESSOR, or ADMIN. "
                    + "An admin cannot change their own role through this endpoint."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated successfully"),
            @ApiResponse(responseCode = "400", description = "Role value is blank/invalid, or the caller tried to change their own role"),
            @ApiResponse(responseCode = "401", description = "Caller is not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is authenticated but is not an admin"),
            @ApiResponse(responseCode = "404", description = "No user exists with the given ID")
    })
    @PostMapping("/users/{id}/role")
    @ResponseBody
    public ResponseEntity<?> updateUserRole(
            @Parameter(description = "ID of the user whose role is being changed") @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String callerEmail = principal.getAttribute("email");
        User caller = userRepository.findByEmail(callerEmail).orElse(null);

        if (caller == null || caller.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error.html", "Only admins can change user roles"));
        }

        Optional<User> targetOpt = userRepository.findById(id);
        if (targetOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User target = targetOpt.get();

        if (target.getId().equals(caller.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error.html", "You cannot change your own role"));
        }

        Role newRole;
        try {
            newRole = Role.valueOf(request.getRole());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error.html", "Invalid role"));
        }

        target.setRole(newRole);
        userRepository.save(target);

        Map<String, String> body = new HashMap<>();
        body.put("message", "Role updated");
        body.put("role", newRole.name());
        return ResponseEntity.ok().body(body);
    }

    @PostMapping("/rooms/update")
    public String updateRoom(@Valid @ModelAttribute RoomDTO updatedRoom,
                             BindingResult bindingResult,
                             @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            redirectAttributes.addFlashAttribute("error", errorMessage);
            return "redirect:/admin/rooms";
        }

        Optional<Room> existingRoomOpt = roomRepository.findById(updatedRoom.getId());

        if (existingRoomOpt.isPresent()) {
            Room existingRoom = existingRoomOpt.get();

            existingRoom.setName(updatedRoom.getName().trim());
            existingRoom.setBuilding(updatedRoom.getBuilding());
            existingRoom.setLocation(updatedRoom.getLocation());
            existingRoom.setFloor(updatedRoom.getFloor());
            existingRoom.setCapacity(updatedRoom.getCapacity());
            existingRoom.setIsAvailable(updatedRoom.getIsAvailable() != null && updatedRoom.getIsAvailable());

            if (imageFile != null && !imageFile.isEmpty()) {
                try {
                    String uploadedUrl = fileStorageService.saveRoomImage(imageFile);
                    existingRoom.setImageUrl(uploadedUrl);
                } catch (IllegalArgumentException e) {
                    redirectAttributes.addFlashAttribute("error", e.getMessage());
                    return "redirect:/admin/rooms";
                } catch (IOException e) {
                    redirectAttributes.addFlashAttribute("error", "Failed to save the uploaded image. Please try again.");
                    return "redirect:/admin/rooms";
                }
            } else {
                existingRoom.setImageUrl(updatedRoom.getImageUrl());
            }

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
    public String addRoom(@Valid @ModelAttribute RoomDTO room,
                          BindingResult bindingResult,
                          @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            redirectAttributes.addFlashAttribute("error", errorMessage);
            return "redirect:/admin/rooms";
        }

        Room newRoom = new Room();
        newRoom.setName(room.getName().trim());
        newRoom.setBuilding(room.getBuilding());
        newRoom.setLocation(room.getLocation());
        newRoom.setFloor(room.getFloor());
        newRoom.setCapacity(room.getCapacity());
        newRoom.setIsAvailable(true);

        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                newRoom.setImageUrl(fileStorageService.saveRoomImage(imageFile));
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("error", e.getMessage());
                return "redirect:/admin/rooms";
            } catch (IOException e) {
                redirectAttributes.addFlashAttribute("error", "Failed to save the uploaded image. Please try again.");
                return "redirect:/admin/rooms";
            }
        } else {
            newRoom.setImageUrl(room.getImageUrl());
        }

        if (room.getAmenities() != null && !room.getAmenities().isEmpty()) {
            newRoom.setAmenities(new ArrayList<>(room.getAmenities()));
        } else {
            newRoom.setAmenities(new ArrayList<>());
        }

        roomRepository.save(newRoom);
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