package com.hua.smartbooking.service;

import com.google.api.services.calendar.model.Event;
import com.hua.smartbooking.dto.BookingRequest;
import com.hua.smartbooking.dto.FinalBookingRequest;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.RoomRepository;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final GoogleCalendarService googleCalendarService;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          GoogleCalendarService googleCalendarService) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
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
        newBooking.setParticipants(request.getParticipants());
        newBooking.setStatus(Booking.BookingStatus.PENDING);

        newBooking = bookingRepository.save(newBooking);

        try {
            List<String> allParticipants = new ArrayList<>();
            if (request.getParticipants() != null) {
                allParticipants.addAll(request.getParticipants());
            }
            if (!allParticipants.contains(organizer.getEmail())) {
                allParticipants.add(organizer.getEmail());
            }

            Event googleEvent = googleCalendarService.createMeetingEvent(
                    organizer.getRefreshToken(),
                    request.getTitle(),
                    startInstant,
                    endInstant,
                    room.getName(),
                    allParticipants
            );

            newBooking.setGoogleEventId(googleEvent.getId());
            newBooking.setStatus(Booking.BookingStatus.APPROVED);

            bookingRepository.save(newBooking);

            Map<String, Object> response = new HashMap<>();
            response.put("bookingId", newBooking.getId());
            response.put("title", request.getTitle());
            response.put("status", "APPROVED");
            response.put("htmlLink", googleEvent.getHtmlLink());

            return response;

        } catch (Exception e) {
            newBooking.setStatus(Booking.BookingStatus.CANCELLED);
            bookingRepository.save(newBooking);
            throw new Exception("Failed to sync with Google Calendar: " + e.getMessage());
        }
    }

}
