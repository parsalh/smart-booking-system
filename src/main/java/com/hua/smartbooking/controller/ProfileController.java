package com.hua.smartbooking.controller;

import com.hua.smartbooking.dto.OutOfOfficeRequest;
import com.hua.smartbooking.dto.TitleRequest;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@Tag(name = "Profile", description = "Manage the logged-in user's own SmartBooking preferences")
public class ProfileController {

    private final UserRepository userRepository;
    private static final ZoneId ATHENS_ZONE = ZoneId.of("Europe/Athens");
    private static final List<String> ALLOWED_TITLES = List.of("Mr.", "Mrs.", "Ms.", "Dr.");

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    public String showProfile(OAuth2AuthenticationToken token, Model model) {
        if (token == null) {
            return "redirect:/login";
        }

        String email = token.getPrincipal().getAttribute("email");
        User dbUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        model.addAttribute("name", dbUser.getFullname());
        model.addAttribute("email", dbUser.getEmail());
        model.addAttribute("avatar", dbUser.getAvatarUrl());
        model.addAttribute("role", dbUser.getRole().name());
        model.addAttribute("title", dbUser.getTitle());

        model.addAttribute("outOfOfficeStart",
                dbUser.getOutOfOfficeStart() != null
                        ? dbUser.getOutOfOfficeStart().atZone(ATHENS_ZONE).toLocalDate().toString()
                        : null);
        model.addAttribute("outOfOfficeEnd",
                dbUser.getOutOfOfficeEnd() != null
                        ? dbUser.getOutOfOfficeEnd().atZone(ATHENS_ZONE).toLocalDate().toString()
                        : null);

        return "profile";
    }

    @Operation(
            summary = "Update the user's title",
            description = "Sets or clears the salutation (Mr./Mrs./Ms./Dr.) shown across SmartBooking for the currently logged-in user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Title saved or cleared successfully"),
            @ApiResponse(responseCode = "400", description = "Title value is blank, or not one of the allowed titles"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    @PostMapping("/api/profile/title")
    @ResponseBody
    public ResponseEntity<?> setTitle(@Valid @RequestBody TitleRequest request,
                                      OAuth2AuthenticationToken token) {
        if (token == null) {
            return ResponseEntity.status(401).build();
        }

        String email = token.getPrincipal().getAttribute("email");
        User dbUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String title = request.getTitle();

        if (title == null || title.isBlank()) {
            dbUser.setTitle(null);
            userRepository.save(dbUser);
            return ResponseEntity.ok().body("Title cleared");
        }

        if (!ALLOWED_TITLES.contains(title)) {
            Map<String, String> body = new HashMap<>();
            body.put("error", "Invalid title");
            return ResponseEntity.badRequest().body(body);
        }

        dbUser.setTitle(title);
        userRepository.save(dbUser);

        return ResponseEntity.ok().body("Title saved");
    }

    @Operation(
            summary = "Set or clear the user's out-of-office period",
            description = "While active, SmartBooking treats this date range as unavailable when suggesting meeting times "
                    + "for the currently logged-in user. Send blank startDate/endDate to clear it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Out-of-office period saved or cleared successfully"),
            @ApiResponse(responseCode = "400", description = "Missing dates, or end date is before start date"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    @PostMapping("/api/profile/out-of-office")
    @ResponseBody
    public ResponseEntity<?> setOutOfOffice(@Valid @RequestBody OutOfOfficeRequest request,
                                            OAuth2AuthenticationToken token) {
        if (token == null) {
            return ResponseEntity.status(401).build();
        }

        String email = token.getPrincipal().getAttribute("email");
        User dbUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getStartDate() == null || request.getEndDate() == null
                || request.getStartDate().isBlank() || request.getEndDate().isBlank()) {
            dbUser.setOutOfOfficeStart(null);
            dbUser.setOutOfOfficeEnd(null);
            userRepository.save(dbUser);
            return ResponseEntity.ok().body("Out-of-office cleared");
        }

        Instant start = LocalDate.parse(request.getStartDate()).atStartOfDay(ATHENS_ZONE).toInstant();
        Instant end = LocalDate.parse(request.getEndDate()).atTime(23, 59, 59).atZone(ATHENS_ZONE).toInstant();

        if (end.isBefore(start)) {
            Map<String, String> body = new HashMap<>();
            body.put("error", "End date must be after start date");
            return ResponseEntity.badRequest().body(body);
        }

        dbUser.setOutOfOfficeStart(start);
        dbUser.setOutOfOfficeEnd(end);
        userRepository.save(dbUser);

        return ResponseEntity.ok().body("Out-of-office saved");
    }
}