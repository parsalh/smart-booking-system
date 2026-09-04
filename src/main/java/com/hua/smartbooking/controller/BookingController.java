package com.hua.smartbooking.controller;

import com.google.api.services.calendar.model.TimePeriod;
import com.hua.smartbooking.dto.*;
import com.hua.smartbooking.enums.RsvpStatus;
import com.hua.smartbooking.exception.StaleGoogleTokenException;
import com.hua.smartbooking.exception.UserNotRegisteredException;
import com.hua.smartbooking.mapper.RoomMapper;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.RoomRepository;
import com.hua.smartbooking.repository.UserRepository;
import com.hua.smartbooking.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Find meeting times, suggest rooms, confirm bookings, and manage RSVPs")
public class BookingController {

    private final AvailabilityService availabilityService;
    private final MeetingOptimizerService optimizerService;
    private final BookingService bookingService;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final GoogleCalendarService googleCalendarService;
    private final EventMappingService eventMappingService;
    private final BookingRepository bookingRepository;

    private final RoomMapper roomMapper;

    public BookingController(AvailabilityService availabilityService,
                             MeetingOptimizerService optimizerService,
                             BookingService bookingService,
                             UserRepository userRepository,
                             RoomRepository roomRepository,
                             GoogleCalendarService googleCalendarService,
                             EventMappingService eventMappingService,
                             RoomMapper roomMapper,
                             BookingRepository bookingRepository) {
        this.availabilityService = availabilityService;
        this.optimizerService = optimizerService;
        this.bookingService = bookingService;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.googleCalendarService = googleCalendarService;
        this.eventMappingService = eventMappingService;
        this.roomMapper = roomMapper;
        this.bookingRepository = bookingRepository;
    }

    @Operation(
            summary = "Find the best available meeting times",
            description = "Runs the scheduling optimizer over the given date range and participants, and returns "
                    + "ranked candidate time slots based on everyone's Google Calendar availability."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranked list of candidate time slots"),
            @ApiResponse(responseCode = "404", description = "One of the participants is not registered on SmartBooking yet — response includes their email so the organizer can invite them"),
            @ApiResponse(responseCode = "409", description = "A participant's Google Calendar connection has expired and needs re-authentication"),
            @ApiResponse(responseCode = "500", description = "Unexpected error.html while computing availability")
    })
    @PostMapping("/suggest-times")
    public ResponseEntity<?> suggestTimes(@Valid @RequestBody BookingRequest request,
                                          @AuthenticationPrincipal OidcUser principal) {
        try {
            String organizerEmail = principal.getAttribute("email");
            User organizer = userRepository.findByEmail(organizerEmail)
                    .orElseThrow(() -> new IllegalStateException("Organizer not found"));

            ZoneId athensZone = ZoneId.of("Europe/Athens");
            ZonedDateTime searchStart = LocalDate.parse(request.getDateRangeStart())
                    .atStartOfDay(athensZone);
            ZonedDateTime searchEnd = LocalDate.parse(request.getDateRangeEnd())
                    .atTime(23, 59, 59).atZone(athensZone);

            List<String> allEmailsToFetch = new ArrayList<>();
            allEmailsToFetch.add(organizerEmail);
            allEmailsToFetch.addAll(request.getRequiredParticipants());
            allEmailsToFetch.addAll(request.getOptionalParticipants());

            for (String email : allEmailsToFetch) {
                User participant = userRepository.findByEmail(email)
                        .orElseThrow(() -> new UserNotRegisteredException("Participant " + email + " is not registered.", email));
                if (participant.getRefreshToken() != null) {
                    try {
                        List<com.google.api.services.calendar.model.Event> googleEvents = googleCalendarService.getUpcomingEvents(participant.getRefreshToken());
                        eventMappingService.syncEvents(googleEvents, participant);
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("invalid_grant")) {
                            participant.setRefreshToken(null);
                            userRepository.save(participant);
                            throw new StaleGoogleTokenException(
                                    "Google Calendar access for " + email + " has expired. They need to sign in again.",
                                    email);
                        }
                        throw e;
                    }
                }
            }

            Map<String, List<TimePeriod>> busyBlocks =
                    availabilityService.fetchGroupAvailability(allEmailsToFetch, searchStart, searchEnd, organizer);

            List<TimeSlotScore> slots = optimizerService.findBestTimeSlots(
                    organizerEmail,
                    searchStart, searchEnd, request.getDurationMinutes(),
                    request.getRequiredParticipants(), request.getOptionalParticipants(), busyBlocks,
                    request.getDailyStartTime(), request.getDailyEndTime(),
                    request.getMaxResults()
            );

            return ResponseEntity.ok(slots);

        } catch (UserNotRegisteredException e) {
            Map<String, String> body = new HashMap<>();
            body.put("status", "requires_invite");
            body.put("missingEmail", e.getMissingEmail());
            return ResponseEntity.status(404).body(body);
        } catch (StaleGoogleTokenException e) {
            Map<String, String> body = new HashMap<>();
            body.put("status", "stale_token");
            body.put("affectedEmail", e.getUserEmail());
            body.put("message", e.getMessage());
            return ResponseEntity.status(409).body(body);
        } catch (Exception e) {
            Map<String, String> body = new HashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    @Operation(
            summary = "Suggest available rooms for a chosen time slot",
            description = "Returns rooms free during the given window, sorted by how well they match the requested "
                    + "amenities and capacity — best matches first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranked list of candidate rooms, best match first"),
            @ApiResponse(responseCode = "500", description = "Unexpected error.html while checking room availability")
    })
    @PostMapping("/suggest-rooms")
    public ResponseEntity<?> suggestRooms(@Valid @RequestBody RoomSuggestionRequest request) {
        try {
            ZonedDateTime start = parseDateLenient(request.getStartTime());
            ZonedDateTime end = parseDateLenient(request.getEndTime());

            List<Room> candidates = roomRepository.findAvailableRooms(
                    request.getMinCapacity(),
                    start.toInstant(),
                    end.toInstant()
            );

            List<String> required = request.getRequiredAmenities() != null
                    ? request.getRequiredAmenities()
                    : List.of();

            List<RoomSuggestionResult> results = candidates.stream()
                    .map(room -> {
                        List<String> roomAmenities = room.getAmenities() != null ? room.getAmenities() : List.of();
                        List<String> missing = required.stream()
                                .filter(a -> !roomAmenities.contains(a))
                                .toList();

                        return new RoomSuggestionResult(
                                room.getId(),
                                room.getName(),
                                room.getBuilding(),
                                room.getLocation(),
                                room.getCapacity(),
                                room.getFloor(),
                                room.getImageUrl(),
                                roomAmenities,
                                missing
                        );
                    })
                    .sorted(
                            Comparator.comparingInt((RoomSuggestionResult r) -> r.getMissingAmenities().size())
                                    .thenComparingInt(RoomSuggestionResult::getCapacity)
                    )
                    .toList();

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            Map<String, String> body = new HashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    @Operation(
            summary = "Confirm and create a meeting booking",
            description = "Books the selected room and time slot, sends calendar invites to participants, and — if "
                    + "repeatWeeks is set — repeats the booking on the same weekday/time for that many weeks, skipping "
                    + "weeks with conflicts unless forcePartial is true."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking(s) created successfully"),
            @ApiResponse(responseCode = "409", description = "The room or a participant is no longer free for one or more of the requested weeks (partial_conflict), or no week could be booked at all"),
            @ApiResponse(responseCode = "500", description = "Unexpected error.html while creating the booking")
    })
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmBooking(@Valid @RequestBody FinalBookingRequest request,
                                            @AuthenticationPrincipal OidcUser principal) {
        try {
            String organizerEmail = principal.getAttribute("email");
            User organizer = userRepository.findByEmail(organizerEmail)
                    .orElseThrow(() -> new IllegalStateException("Organizer not found"));

            int weeks = (request.getRepeatWeeks() != null && request.getRepeatWeeks() > 0) ? request.getRepeatWeeks() : 1;
            boolean forcePartial = Boolean.TRUE.equals(request.getForcePartial());

            List<ZonedDateTime> successfulStarts = new ArrayList<>();
            List<Map<String, String>> failedDates = new ArrayList<>();

            ZonedDateTime baseStart = parseDateLenient(request.getStartTime());
            ZonedDateTime baseEnd = parseDateLenient(request.getEndTime());
            long durationMinutes = Duration.between(baseStart, baseEnd).toMinutes();

            List<String> emailsToCheck = new ArrayList<>();
            emailsToCheck.add(organizerEmail);
            if (request.getParticipants() != null) {
                emailsToCheck.addAll(request.getParticipants());
            }

            for (String email : emailsToCheck) {
                User participant = userRepository.findByEmail(email).orElse(null);
                if (participant != null && participant.getRefreshToken() != null) {
                    List<com.google.api.services.calendar.model.Event> googleEvents = googleCalendarService.getUpcomingEvents(participant.getRefreshToken());
                    eventMappingService.syncEvents(googleEvents, participant);
                }
            }

            ZonedDateTime maxSearchEnd = baseEnd.plusWeeks(weeks);
            Map<String, List<TimePeriod>> busyBlocks = availabilityService.fetchGroupAvailability(
                    emailsToCheck, baseStart, maxSearchEnd, organizer
            );

            for (int i = 0; i < weeks; i++) {
                ZonedDateTime currentStart = baseStart.plusWeeks(i);
                ZonedDateTime currentEnd = currentStart.plusMinutes(durationMinutes);

                List<Room> availableRooms = roomRepository.findAvailableRooms(
                        request.getParticipants().size() + 1,
                        currentStart.toInstant(),
                        currentEnd.toInstant()
                );
                boolean isRoomFree = availableRooms.stream().anyMatch(r -> r.getId().equals(request.getRoomId()));

                if (!isRoomFree) {
                    Map<String, String> conflict = new HashMap<>();
                    conflict.put("date", currentStart.toLocalDate().toString());
                    conflict.put("reason", "The room is unavailable.");
                    failedDates.add(conflict);
                    continue;
                }

                boolean isUserBusy = false;
                String busyUserEmail = "";

                for (String email : emailsToCheck) {
                    if (isParticipantBusy(email, currentStart, currentEnd, busyBlocks)) {
                        isUserBusy = true;
                        busyUserEmail = email;
                        break;
                    }
                }

                if (isUserBusy) {
                    Map<String, String> conflict = new HashMap<>();
                    conflict.put("date", currentStart.toLocalDate().toString());
                    conflict.put("reason", "Participant " + busyUserEmail + " has a scheduling conflict.");
                    failedDates.add(conflict);
                } else {
                    successfulStarts.add(currentStart);
                }
            }

            if (!failedDates.isEmpty() && !forcePartial) {
                Map<String, Object> conflictResponse = new HashMap<>();
                conflictResponse.put("status", "partial_conflict");
                conflictResponse.put("successfulDates", successfulStarts);
                conflictResponse.put("failedDates", failedDates);

                return ResponseEntity.status(409).body(conflictResponse);
            }

            if (successfulStarts.isEmpty()) {
                Map<String, String> body = new HashMap<>();
                body.put("error", "No available slots found to book for the selected weeks.");
                return ResponseEntity.status(409).body(body);
            }

            List<Map<String, Object>> allResponses = new ArrayList<>();

            for (ZonedDateTime start : successfulStarts) {
                ZonedDateTime end = start.plusMinutes(durationMinutes);

                java.time.format.DateTimeFormatter strictFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
                request.setStartTime(start.format(strictFormatter));
                request.setEndTime(end.format(strictFormatter));

                Map<String, Object> response = bookingService.createBooking(request, organizer);
                allResponses.add(response);
            }

            Map<String, Object> finalResponse = allResponses.get(0);
            finalResponse.put("bookedInstances", successfulStarts.size());

            return ResponseEntity.ok(finalResponse);

        } catch (IllegalStateException e) {
            Map<String, String> body = new HashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.status(409).body(body);
        } catch (Exception e) {
            Map<String, String> body = new HashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    @Operation(
            summary = "Respond to a meeting invitation",
            description = "Records the currently logged-in user's RSVP (accepted/declined/tentative) for a booking they were invited to."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "RSVP recorded successfully"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @ApiResponse(responseCode = "500", description = "Unexpected error.html while updating the RSVP")
    })
    @PostMapping("/{bookingId}/rsvp")
    public ResponseEntity<?> respondToInvite(
            @Parameter(description = "ID of the booking being responded to") @PathVariable Long bookingId,
            @Valid @RequestBody RsvpRequest request,
            @AuthenticationPrincipal OidcUser principal) {

        try {
            if (principal == null) {
                return ResponseEntity.status(401).body(java.util.Map.of("error.html", "Unauthorized access."));
            }

            String userEmail = principal.getAttribute("email");
            bookingService.updateRsvpStatus(bookingId, userEmail, request.getStatus());

            return ResponseEntity.ok().body(java.util.Map.of("message", "RSVP updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error.html", e.getMessage()));
        }
    }

    @Operation(
            summary = "List the user's pending meeting invitations",
            description = "Returns bookings the currently logged-in user has been invited to but hasn't responded to yet."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of pending invites (empty if none)"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    @GetMapping("/pending-invites")
    public ResponseEntity<List<PendingInviteDTO>> getMyPendingInvites(@AuthenticationPrincipal OidcUser principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String userEmail = principal.getAttribute("email");
        List<PendingInviteDTO> pendingInvites = bookingService.getPendingInvitesForUser(userEmail);

        return ResponseEntity.ok(pendingInvites);
    }

    @Operation(
            summary = "List the user's SmartBooking-created meetings",
            description = "Returns meetings that were scheduled via SmartBooking's optimizer for the currently logged-in user, "
                    + "for display on their calendar/dashboard."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of SmartBooking meetings (empty if none)"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    @GetMapping("/my-smartbookings")
    public ResponseEntity<List<Map<String, Object>>> getMySmartBookings(@AuthenticationPrincipal OidcUser principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        String userEmail = principal.getAttribute("email");
        return ResponseEntity.ok(bookingService.getSmartBookingsForUser(userEmail));
    }

    @Operation(
            summary = "Get a booking's participants and their RSVP status",
            description = "Reconciles each participant's RSVP against Google Calendar first, then returns the current status per (decrypted) email."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Map of participant email to RSVP status"),
            @ApiResponse(responseCode = "404", description = "Booking not found")
    })
    @GetMapping("/{bookingId}/participants")
    public ResponseEntity<?> getParticipants(
            @Parameter(description = "ID of the booking") @PathVariable Long bookingId) {
        return bookingRepository.findById(bookingId)
                .<ResponseEntity<?>>map(booking -> {
                    com.hua.smartbooking.util.StringCryptoConverter crypto = new com.hua.smartbooking.util.StringCryptoConverter();

                    for (String key : new HashSet<>(booking.getParticipants().keySet())) {
                        try {
                            String plainEmail = crypto.convertToEntityAttribute(key);
                            bookingService.reconcileRsvpFromGoogle(booking, plainEmail != null ? plainEmail : key);
                        } catch (Exception e) {
                            bookingService.reconcileRsvpFromGoogle(booking, key);
                        }
                    }

                    Map<String, RsvpStatus> decrypted = new HashMap<>();
                    for (Map.Entry<String, RsvpStatus> entry : booking.getParticipants().entrySet()) {
                        try {
                            String dec = crypto.convertToEntityAttribute(entry.getKey());
                            decrypted.put(dec != null ? dec : entry.getKey(), entry.getValue());
                        } catch (Exception e) {
                            decrypted.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return ResponseEntity.ok(decrypted);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ZonedDateTime parseDateLenient(String dateStr) {
        if (dateStr == null) return null;

        if (dateStr.length() == 22) {
            dateStr = dateStr.substring(0, 16) + ":00" + dateStr.substring(16);
        }
        else if (dateStr.length() == 17 && dateStr.endsWith("Z")) {
            dateStr = dateStr.substring(0, 16) + ":00Z";
        }

        return ZonedDateTime.parse(dateStr);
    }

    private boolean isParticipantBusy(String email, ZonedDateTime start, ZonedDateTime end, Map<String, List<TimePeriod>> allBusyBlocks) {
        List<TimePeriod> blocks = allBusyBlocks.getOrDefault(email, new ArrayList<>());
        long startMillis = start.toInstant().toEpochMilli();
        long endMillis = end.toInstant().toEpochMilli();

        for (TimePeriod block : blocks) {
            long blockStart = block.getStart().getValue();
            long blockEnd = block.getEnd().getValue();
            if (startMillis < blockEnd && endMillis > blockStart) {
                return true;
            }
        }
        return false;
    }
}