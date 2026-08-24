package com.hua.smartbooking.controller;

import com.hua.smartbooking.dto.InviteRequest;
import com.hua.smartbooking.repository.UserRepository;
import com.hua.smartbooking.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class InviteController {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public InviteController(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @GetMapping("/api/users/check")
    public ResponseEntity<Map<String, Boolean>> checkUserExists(@RequestParam String email) {
        boolean exists = userRepository.findByEmail(email.toLowerCase().trim()).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @PostMapping("/api/invite/send")
    public ResponseEntity<?> sendInvite(@RequestBody InviteRequest request, @AuthenticationPrincipal OidcUser principal) {
        String organizerName = principal.getAttribute("name");
        emailService.sendInvitationEmail(request.getEmail(), organizerName);
        return ResponseEntity.ok().body("Invitation sent");
    }
}