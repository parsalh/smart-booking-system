package com.hua.smartbooking.service;

import com.hua.smartbooking.enums.BookingStatus;
import com.hua.smartbooking.enums.RsvpStatus;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.EventRepository;
import com.hua.smartbooking.repository.RoomRepository;
import com.hua.smartbooking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {

    private BookingRepository bookingRepository;
    private GoogleCalendarService googleCalendarService;
    private EventRepository eventRepository;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        googleCalendarService = mock(GoogleCalendarService.class);
        eventRepository = mock(EventRepository.class);

        bookingService = new BookingService(bookingRepository, roomRepository, userRepository, googleCalendarService, eventRepository);
    }

    private Booking bookingWithParticipant(String email, RsvpStatus status) {
        Booking booking = new Booking();
        booking.setId(1L);
        Map<String, RsvpStatus> participants = new HashMap<>();
        participants.put(email, status);
        booking.setParticipants(participants);
        return booking;
    }

    @Test
    void updatesStatusForMatchedParticipantAndSaves() {
        Booking booking = bookingWithParticipant("guest@hua.gr", RsvpStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.updateRsvpStatus(1L, "guest@hua.gr", RsvpStatus.ACCEPTED);

        assertThat(booking.getParticipants().get("guest@hua.gr")).isEqualTo(RsvpStatus.ACCEPTED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void matchingIsCaseInsensitiveAndTrimmed() {
        // Stored key has different case/whitespace than what the caller passes in —
        // this is exactly the class of bug we found with the old encrypted-key
        // matching, so it's worth pinning down explicitly for plain-text keys too.
        Booking booking = bookingWithParticipant("Guest@HUA.gr", RsvpStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.updateRsvpStatus(1L, "  guest@hua.gr  ", RsvpStatus.ACCEPTED);

        assertThat(booking.getParticipants().get("Guest@HUA.gr")).isEqualTo(RsvpStatus.ACCEPTED);
    }

    @Test
    void throwsWhenBookingNotFound() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.updateRsvpStatus(99L, "guest@hua.gr", RsvpStatus.ACCEPTED))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void throwsWhenCallerIsNotAParticipant() {
        Booking booking = bookingWithParticipant("someoneelse@hua.gr", RsvpStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateRsvpStatus(1L, "guest@hua.gr", RsvpStatus.ACCEPTED))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not invited");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void syncsAcceptedStatusToGoogleCalendarWhenGoogleEventIdPresent() throws Exception {
        Booking booking = bookingWithParticipant("guest@hua.gr", RsvpStatus.PENDING);
        booking.setGoogleEventId("google-event-123");
        User organizer = new User();
        organizer.setRefreshToken("organizer-refresh-token");
        booking.setUser(organizer);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.updateRsvpStatus(1L, "guest@hua.gr", RsvpStatus.ACCEPTED);

        verify(googleCalendarService).updateEventRsvpOnGoogleCalendar(
                "organizer-refresh-token", "google-event-123", "guest@hua.gr", "accepted");
    }

    @Test
    void syncsDeclinedStatusToGoogleCalendar() throws Exception {
        Booking booking = bookingWithParticipant("guest@hua.gr", RsvpStatus.PENDING);
        booking.setGoogleEventId("google-event-123");
        User organizer = new User();
        organizer.setRefreshToken("organizer-refresh-token");
        booking.setUser(organizer);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.updateRsvpStatus(1L, "guest@hua.gr", RsvpStatus.DECLINED);

        verify(googleCalendarService).updateEventRsvpOnGoogleCalendar(
                "organizer-refresh-token", "google-event-123", "guest@hua.gr", "declined");
    }

    @Test
    void doesNotAttemptGoogleSyncWhenNoGoogleEventId() throws Exception {
        Booking booking = bookingWithParticipant("guest@hua.gr", RsvpStatus.PENDING);
        // googleEventId intentionally left null (not a real SmartBooking-synced event)
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.updateRsvpStatus(1L, "guest@hua.gr", RsvpStatus.ACCEPTED);

        verifyNoInteractions(googleCalendarService);
    }

    @Test
    void doesNotPropagateWhenGoogleSyncFails() throws Exception {
        // The DB update already succeeded by this point — a Google API hiccup
        // shouldn't turn a successful RSVP into a user-facing error.
        Booking booking = bookingWithParticipant("guest@hua.gr", RsvpStatus.PENDING);
        booking.setGoogleEventId("google-event-123");
        User organizer = new User();
        organizer.setRefreshToken("organizer-refresh-token");
        booking.setUser(organizer);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        doThrow(new RuntimeException("Google API is down"))
                .when(googleCalendarService).updateEventRsvpOnGoogleCalendar(any(), any(), any(), any());

        bookingService.updateRsvpStatus(1L, "guest@hua.gr", RsvpStatus.ACCEPTED);

        assertThat(booking.getParticipants().get("guest@hua.gr")).isEqualTo(RsvpStatus.ACCEPTED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void organizerCanCancelTheirOwnBooking() {
        Booking booking = new Booking();
        booking.setId(1L);
        User organizer = new User();
        organizer.setEmail("organizer@hua.gr");
        booking.setUser(organizer);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(1L, "organizer@hua.gr");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void nonOrganizerCannotCancelTheBooking() {
        Booking booking = new Booking();
        booking.setId(1L);
        User organizer = new User();
        organizer.setEmail("organizer@hua.gr");
        booking.setUser(organizer);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(1L, "someone-else@hua.gr"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("organizer");

        assertThat(booking.getStatus()).isNotEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancellingDeletesAnyStaleEventTableCopy() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setGoogleEventId("google-event-123");
        User organizer = new User();
        organizer.setEmail("organizer@hua.gr");
        booking.setUser(organizer);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(1L, "organizer@hua.gr");

        verify(eventRepository).deleteByGoogleEventId("google-event-123");
    }
}