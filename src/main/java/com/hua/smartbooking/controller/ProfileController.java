package com.hua.smartbooking.controller;

import com.hua.smartbooking.dto.OutOfOfficeRequest;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ProfileController {

    private final UserRepository userRepository;
    private static final ZoneId ATHENS_ZONE = ZoneId.of("Europe/Athens");

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

    @PostMapping("/api/profile/out-of-office")
    @ResponseBody
    public ResponseEntity<?> setOutOfOffice(@RequestBody OutOfOfficeRequest request,
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