package com.hua.smartbooking.controller;

import com.hua.smartbooking.dto.UserSearchResult;
import com.hua.smartbooking.enums.BookingStatus;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Search for other SmartBooking users when adding meeting participants")
public class UserController {

    private static final ZoneId ATHENS_ZONE = ZoneId.of("Europe/Athens");
    private static final int FREQUENT_COLLABORATOR_THRESHOLD = 5;
    private static final int FREQUENT_COLLABORATOR_LIMIT = 5;

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public UserController(UserRepository userRepository, BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @Operation(
            summary = "Search registered users by name or email",
            description = "Used when adding participants to a meeting. Excludes the currently logged-in user from the results."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching users (empty list if none, or if the query is blank)")
    })
    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResult>> searchUsers(
            @Parameter(description = "Search text — matched against full name and email", example = "jane")
            @RequestParam("q") String query,
            @AuthenticationPrincipal OAuth2User principal) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        List<User> users = userRepository.findByFullnameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);

        String currentUserEmail = principal != null ? principal.getAttribute("email") : null;

        if (currentUserEmail != null) {
            users = users.stream()
                    .filter(user -> !currentUserEmail.equalsIgnoreCase(user.getEmail()))
                    .collect(Collectors.toList());
        }

        List<UserSearchResult> results = users.stream()
                .map(this::toSearchResult)
                .toList();

        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "List the user's frequent meeting collaborators",
            description = "Returns up to 5 people the currently logged-in user has organized meetings with or been "
                    + "invited by at least " + FREQUENT_COLLABORATOR_THRESHOLD + " times, most-frequent first. "
                    + "Used to suggest quick-add participants when booking a meeting."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of frequent collaborators (empty if none qualify)")
    })
    @GetMapping("/frequent-collaborators")
    public ResponseEntity<List<UserSearchResult>> frequentCollaborators(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        String organizerEmail = principal.getAttribute("email");
        User organizer = userRepository.findByEmail(organizerEmail).orElse(null);

        if (organizer == null) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        List<Booking> allBookings = bookingRepository.findByStatusNot(BookingStatus.CANCELLED);
        String normalizedOrganizerEmail = organizerEmail.toLowerCase().trim();

        Map<String, Integer> countsByEmail = new HashMap<>();
        for (Booking booking : allBookings) {
            boolean organizedByCurrentUser = booking.getUser() != null
                    && normalizedOrganizerEmail.equals(booking.getUser().getEmail().toLowerCase().trim());

            if (organizedByCurrentUser) {
                for (String participantEmail : booking.getParticipants().keySet()) {
                    String normalized = participantEmail.toLowerCase().trim();
                    if (!normalized.equals(normalizedOrganizerEmail)) {
                        countsByEmail.merge(normalized, 1, Integer::sum);
                    }
                }
            } else {
                boolean currentUserWasInvited = booking.getParticipants().keySet().stream()
                        .anyMatch(email -> email.toLowerCase().trim().equals(normalizedOrganizerEmail));

                if (currentUserWasInvited && booking.getUser() != null) {
                    String organizerNormalized = booking.getUser().getEmail().toLowerCase().trim();
                    countsByEmail.merge(organizerNormalized, 1, Integer::sum);
                }
            }
        }

        List<UserSearchResult> frequentCollaborators = countsByEmail.entrySet().stream()
                .filter(entry -> entry.getValue() >= FREQUENT_COLLABORATOR_THRESHOLD)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(FREQUENT_COLLABORATOR_LIMIT)
                .map(entry -> userRepository.findByEmail(entry.getKey()).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toSearchResult)
                .toList();

        return ResponseEntity.ok(frequentCollaborators);
    }

    private UserSearchResult toSearchResult(User user) {
        return new UserSearchResult(
                user.getId(),
                user.getFullname(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getOutOfOfficeStart() != null
                        ? user.getOutOfOfficeStart().atZone(ATHENS_ZONE).toLocalDate().toString()
                        : null,
                user.getOutOfOfficeEnd() != null
                        ? user.getOutOfOfficeEnd().atZone(ATHENS_ZONE).toLocalDate().toString()
                        : null
        );
    }
}