package com.hua.smartbooking.controller;

import com.google.api.services.calendar.model.TimePeriod;
import com.hua.smartbooking.dto.BookingRequest;
import com.hua.smartbooking.dto.FinalBookingRequest;
import com.hua.smartbooking.dto.TimeSlotScore;
import com.hua.smartbooking.exception.UserNotRegisteredException;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.RoomRepository;
import com.hua.smartbooking.repository.UserRepository;
import com.hua.smartbooking.service.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final AvailabilityService availabilityService;
    private final MeetingOptimizerService optimizerService;
    private final BookingService bookingService;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final GoogleCalendarService googleCalendarService;
    private final EventMappingService eventMappingService;

    public BookingController(AvailabilityService availabilityService,
                             MeetingOptimizerService optimizerService,
                             BookingService bookingService,
                             UserRepository userRepository,
                             RoomRepository roomRepository,
                             GoogleCalendarService googleCalendarService,
                             EventMappingService eventMappingService) {
        this.availabilityService = availabilityService;
        this.optimizerService = optimizerService;
        this.bookingService = bookingService;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.googleCalendarService = googleCalendarService;
        this.eventMappingService = eventMappingService;
    }

    @PostMapping("/suggest-times")
    public ResponseEntity<?> suggestTimes(@RequestBody BookingRequest request,
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
                    List<com.google.api.services.calendar.model.Event> googleEvents = googleCalendarService.getUpcomingEvents(participant.getRefreshToken());
                    eventMappingService.syncEvents(googleEvents, participant);
                }
            }

            Map<String, List<TimePeriod>> busyBlocks =
                    availabilityService.fetchGroupAvailability(allEmailsToFetch, searchStart, searchEnd, organizer);

            List<TimeSlotScore> slots = optimizerService.findBestTimeSlots(
                    organizerEmail,
                    searchStart, searchEnd, request.getDurationMinutes(),
                    request.getRequiredParticipants(), request.getOptionalParticipants(), busyBlocks
            );

            return ResponseEntity.ok(slots);

        } catch (UserNotRegisteredException e) {
            Map<String, String> body = new HashMap<>();
            body.put("status", "requires_invite");
            body.put("missingEmail", e.getMissingEmail());
            return ResponseEntity.status(404).body(body);
        } catch (Exception e) {
            Map<String, String> body = new HashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    @Data
    public static class RoomSuggestionRequest {
        private String startTime;
        private String endTime;
        private Integer minCapacity;
        private List<String> requiredAmenities;
    }

    @Data
    @AllArgsConstructor
    public static class RoomSuggestionResult {
        private Long id;
        private String name;
        private Integer capacity;
        private String floor;
        private String imageUrl;
        private List<String> amenities;
        private List<String> missingAmenities;
    }

    @PostMapping("/suggest-rooms")
    public ResponseEntity<?> suggestRooms(@RequestBody RoomSuggestionRequest request) {
        try {
            ZonedDateTime start = ZonedDateTime.parse(request.getStartTime());
            ZonedDateTime end = ZonedDateTime.parse(request.getEndTime());

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
                                room.getId(), room.getName(), room.getCapacity(),
                                room.getFloor(), room.getImageUrl(), roomAmenities, missing
                        );
                    })
                    .sorted(Comparator.comparingInt(r -> r.getMissingAmenities().size()))
                    .toList();

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            Map<String, String> body = new HashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmBooking(@RequestBody FinalBookingRequest request,
                                            @AuthenticationPrincipal OidcUser principal) {
        try {
            String organizerEmail = principal.getAttribute("email");
            User organizer = userRepository.findByEmail(organizerEmail)
                    .orElseThrow(() -> new IllegalStateException("Organizer not found"));

            Map<String, Object> bookingResponse = bookingService.createBooking(request, organizer);
            return ResponseEntity.ok(bookingResponse);

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
}