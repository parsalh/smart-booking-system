package com.hua.smartbooking.controller;

import com.hua.smartbooking.dto.InviteRequest;
import com.hua.smartbooking.repository.UserRepository;
import com.hua.smartbooking.service.EmailService;
import com.hua.smartbooking.service.InviteRateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "Invitations", description = "Invite unregistered users to SmartBooking by email")
public class InviteController {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final InviteRateLimiterService inviteRateLimiterService;

    public InviteController(UserRepository userRepository, EmailService emailService,
                            InviteRateLimiterService inviteRateLimiterService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.inviteRateLimiterService = inviteRateLimiterService;
    }

    @Operation(
            summary = "Check if an email belongs to a registered SmartBooking user",
            description = "Used while adding participants to a meeting, to decide whether an invite email is needed first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Returns whether a user with this email exists")
    })
    @GetMapping("/api/users/check")
    public ResponseEntity<Map<String, Boolean>> checkUserExists(
            @Parameter(description = "Email address to look up", example = "jane@hua.gr")
            @RequestParam String email) {
        boolean exists = userRepository.findByEmail(email.toLowerCase().trim()).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @Operation(
            summary = "Send a SmartBooking invitation email",
            description = "Emails the given address inviting them to sign up for SmartBooking, on behalf of the currently logged-in user. "
                    + "Rate-limited: at most one invite per target email per 24h, and a daily cap per organizer."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation email sent successfully"),
            @ApiResponse(responseCode = "400", description = "Email is missing or not a valid email address"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded — either this email was already invited recently, or the organizer has sent too many invites today"),
            @ApiResponse(responseCode = "500", description = "Email could not be sent (e.g. mail provider error)")
    })
    @PostMapping("/api/invite/send")
    public ResponseEntity<?> sendInvite(@Valid @RequestBody InviteRequest request, @AuthenticationPrincipal OidcUser principal) {
        String organizerEmail = principal.getAttribute("email");
        String organizerName = principal.getAttribute("name");

        String rateLimitError = inviteRateLimiterService.checkAndRecord(organizerEmail, request.getEmail());
        if (rateLimitError != null) {
            return ResponseEntity.status(429).body(Map.of("error", rateLimitError));
        }

        try {
            emailService.sendInvitationEmail(request.getEmail(), organizerName);
            return ResponseEntity.ok().body(Map.of("message", "Invitation sent"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }
}