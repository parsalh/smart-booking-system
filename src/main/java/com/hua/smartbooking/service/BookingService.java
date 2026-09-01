package com.hua.smartbooking.service;

import com.google.api.services.calendar.model.Event;
import com.hua.smartbooking.dto.FinalBookingRequest;
import com.hua.smartbooking.dto.PendingInviteDTO;
import com.hua.smartbooking.enums.BookingStatus;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.RoomRepository;
import com.hua.smartbooking.repository.UserRepository;
import com.hua.smartbooking.util.StringCryptoConverter;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import com.hua.smartbooking.enums.RsvpStatus;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GoogleCalendarService googleCalendarService;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          UserRepository userRepository,
                          GoogleCalendarService googleCalendarService) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.googleCalendarService = googleCalendarService;
    }

    @CacheEvict(value = "calendarEvents", key = "#organizer.refreshToken")
    @Transactional
    public Map<String, Object> createBooking(FinalBookingRequest request, User organizer) throws Exception {

        Instant startInstant = Instant.parse(request.getStartTime());
        Instant endInstant = Instant.parse(request.getEndTime());

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isOccupied = bookingRepository.hasConflictingBookings(room.getId(), startInstant, endInstant);
        if (isOccupied) {
            throw new IllegalStateException("Room " + room.getName() + " was just booked by someone else. Please select another time or room.");
        }

        Booking newBooking = new Booking();
        newBooking.setUser(organizer);
        newBooking.setRoom(room);
        newBooking.setStartTime(startInstant);
        newBooking.setEndTime(endInstant);
        newBooking.setStatus(BookingStatus.PENDING);
        newBooking.setTitle(request.getTitle());

        Map<String, RsvpStatus> rsvpMap = new HashMap<>();
        List<String> googleAttendees = new ArrayList<>();

        String organizerEmail = organizer.getEmail().toLowerCase().trim();
        rsvpMap.put(organizerEmail, RsvpStatus.ACCEPTED);
        googleAttendees.add(organizerEmail);

        if (request.getParticipants() != null) {
            for (String email : request.getParticipants()) {
                String normalized = email.toLowerCase().trim();
                if (!normalized.equals(organizerEmail)) {
                    rsvpMap.put(normalized, RsvpStatus.PENDING);
                    googleAttendees.add(normalized);
                }
            }
        }

        newBooking.setParticipants(rsvpMap);

        newBooking = bookingRepository.save(newBooking);

        try {
            Event googleEvent = googleCalendarService.createMeetingEvent(
                    organizer.getRefreshToken(),
                    request.getTitle(),
                    startInstant,
                    endInstant,
                    room.getName(),
                    googleAttendees,
                    organizerEmail
            );

            newBooking.setGoogleEventId(googleEvent.getId());
            newBooking.setStatus(BookingStatus.APPROVED);

            bookingRepository.save(newBooking);

            Map<String, Object> response = new HashMap<>();
            response.put("bookingId", newBooking.getId());
            response.put("title", request.getTitle());
            response.put("status", "APPROVED");
            response.put("htmlLink", googleEvent.getHtmlLink());

            return response;

        } catch (Exception e) {
            newBooking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(newBooking);
            throw new Exception("Failed to sync with Google Calendar: " + e.getMessage());
        }
    }

    @Transactional
    public void updateRsvpStatus(Long bookingId, String userEmail, RsvpStatus newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        String targetEmail = userEmail.toLowerCase().trim();

        String matchedKey = booking.getParticipants().keySet().stream()
                .filter(key -> key != null && key.toLowerCase().trim().equals(targetEmail))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("You are not invited to this booking."));

        booking.getParticipants().put(matchedKey, newStatus);
        bookingRepository.save(booking);

        if (booking.getGoogleEventId() != null) {
            try {
                String googleStatus = newStatus == RsvpStatus.ACCEPTED ? "accepted" : "declined";
                googleCalendarService.updateEventRsvpOnGoogleCalendar(
                        booking.getUser().getRefreshToken(),
                        booking.getGoogleEventId(),
                        userEmail,
                        googleStatus
                );
            } catch (Exception e) {
                System.err.println("Failed to sync RSVP with Google: " + e.getMessage());
            }
        }
    }

    public List<PendingInviteDTO> getPendingInvitesForUser(String userEmail) {
        String targetEmail = userEmail.toLowerCase().trim();

        List<Booking> candidates = bookingRepository.findByStartTimeAfterAndStatusNot(
                Instant.now(), BookingStatus.CANCELLED);

        return candidates.stream()
                .filter(booking -> {
                    reconcileRsvpFromGoogle(booking, targetEmail);
                    return booking.getParticipants().entrySet().stream()
                            .anyMatch(entry -> entry.getKey().toLowerCase().trim().equals(targetEmail)
                                    && entry.getValue() == RsvpStatus.PENDING);
                })
                .map(booking -> {
                    String orgName = booking.getUser() != null && booking.getUser().getFullname() != null
                            ? booking.getUser().getFullname() : "Unknown";
                    String rName = booking.getRoom() != null ? booking.getRoom().getName() : "Unknown Room";
                    String meetingTitle = booking.getTitle() != null ? booking.getTitle() : "SmartBooking Meeting";

                    return new PendingInviteDTO(
                            booking.getId(), meetingTitle, orgName, booking.getUser().getEmail(),
                            rName, booking.getStartTime(), booking.getEndTime());
                })
                .collect(Collectors.toList());
    }

    public void reconcileRsvpFromGoogle(Booking booking, String userEmail) {
        if (booking.getGoogleEventId() == null) return;

        try {
            String googleStatus = googleCalendarService.getAttendeeResponseStatus(
                    booking.getUser().getRefreshToken(),
                    booking.getGoogleEventId(),
                    userEmail
            );

            if (googleStatus == null) return;

            RsvpStatus mapped = switch (googleStatus) {
                case "accepted" -> RsvpStatus.ACCEPTED;
                case "declined" -> RsvpStatus.DECLINED;
                case "tentative" -> RsvpStatus.TENTATIVE;
                default -> null;
            };

            if (mapped != null) {
                com.hua.smartbooking.util.StringCryptoConverter crypto = new com.hua.smartbooking.util.StringCryptoConverter();

                booking.getParticipants().keySet().stream()
                        .filter(key -> {
                            try {
                                String dec = crypto.convertToEntityAttribute(key);
                                return dec != null && dec.equalsIgnoreCase(userEmail);
                            } catch (Exception e) {
                                return key.equalsIgnoreCase(userEmail);
                            }
                        })
                        .findFirst()
                        .ifPresent(key -> {
                            if (booking.getParticipants().get(key) != mapped) {
                                booking.getParticipants().put(key, mapped);
                                bookingRepository.save(booking);
                            }
                        });
            }
        } catch (Exception e) {
            System.err.println("Could not reconcile RSVP from Google: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getSmartBookingsForUser(String userEmail) {
        String targetEmail = userEmail.toLowerCase().trim();
        com.hua.smartbooking.util.StringCryptoConverter crypto = new com.hua.smartbooking.util.StringCryptoConverter();

        return bookingRepository.findAll().stream()
                .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
                .filter(booking -> {
                    if (booking.getUser() != null && booking.getUser().getEmail().equalsIgnoreCase(targetEmail)) {
                        return true;
                    }
                    for (String key : booking.getParticipants().keySet()) {
                        try {
                            String decryptedKey = crypto.convertToEntityAttribute(key);
                            if (decryptedKey != null && decryptedKey.toLowerCase().trim().equals(targetEmail)) {
                                return true;
                            }
                        } catch (Exception e) {
                            if (key.toLowerCase().trim().equals(targetEmail)) return true;
                        }
                    }
                    return false;
                })
                .sorted(Comparator.comparing(Booking::getStartTime))
                .map(booking -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", booking.getId());
                    map.put("title", booking.getRoom() != null ? booking.getRoom().getName() : "SmartBooking");
                    map.put("startTime", booking.getStartTime().toString());
                    map.put("endTime", booking.getEndTime() != null ? booking.getEndTime().toString() : null);

                    Map<String, Object> roomMap = new HashMap<>();
                    if (booking.getRoom() != null) roomMap.put("name", booking.getRoom().getName());
                    map.put("room", roomMap);

                    Map<String, RsvpStatus> decryptedParticipants = new HashMap<>();
                    for (Map.Entry<String, RsvpStatus> entry : booking.getParticipants().entrySet()) {
                        try {
                            String dec = crypto.convertToEntityAttribute(entry.getKey());
                            decryptedParticipants.put(dec != null ? dec : entry.getKey(), entry.getValue());
                        } catch (Exception e) {
                            decryptedParticipants.put(entry.getKey(), entry.getValue());
                        }
                    }
                    map.put("participants", decryptedParticipants);

                    String myStatus = "PENDING";
                    if (booking.getUser() != null && booking.getUser().getEmail().equalsIgnoreCase(targetEmail)) {
                        myStatus = "ACCEPTED";
                    } else {
                        myStatus = decryptedParticipants.getOrDefault(targetEmail, RsvpStatus.PENDING).name();
                    }
                    map.put("myRsvpStatus", myStatus);

                    return map;
                })
                .collect(Collectors.toList());
    }
}
