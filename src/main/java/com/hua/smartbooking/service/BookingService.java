package com.hua.smartbooking.service;

import com.hua.smartbooking.dto.BookingRequest;
import com.hua.smartbooking.dto.FinalBookingRequest;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.RoomRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

    @Transactional
    public Booking createBooking(FinalBookingRequest request, User organizer) throws Exception {

        LocalDateTime localStart = LocalDateTime.parse(request.getStartTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateTime localEnd = LocalDateTime.parse(request.getEndTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ZoneId athensZone = ZoneId.of("Europe/Athens");
        Instant startInstant = localStart.atZone(athensZone).toInstant();
        Instant endInstant = localEnd.atZone(athensZone).toInstant();


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
        newBooking.setStatus(Booking.BookingStatus.PENDING);

        newBooking = bookingRepository.save(newBooking);

        try {
            String googleEventId = googleCalendarService.createMeetingEvent(
                    organizer.getRefreshToken(),
                    request.getTitle(),
                    startInstant,
                    endInstant,
                    room.getName(),
                    request.getParticipants()
            );

            newBooking.setGoogleEventId(googleEventId);
            newBooking.setStatus(Booking.BookingStatus.APPROVED);

            return bookingRepository.save(newBooking);
        } catch (Exception e) {
            newBooking.setStatus(Booking.BookingStatus.CANCELLED);
            bookingRepository.save(newBooking);
            throw new Exception("Failed to sync with Google Calendar: " + e.getMessage());
        }
    }

}
