package com.hua.smartbooking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import com.hua.smartbooking.enums.RsvpStatus;
import com.hua.smartbooking.enums.BookingStatus;
import com.hua.smartbooking.factory.GoogleCalendarClientFactory;
import com.hua.smartbooking.model.Booking;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.mapper.EventMapper;
import com.hua.smartbooking.repository.BookingRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for interacting with Google Calendar API and mapping to internal Entities.
 */
@Service
public class GoogleCalendarService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final EventMapper eventMapper;
    private final GoogleCalendarClientFactory calendarClientFactory;
    private final BookingRepository bookingRepository;

    public GoogleCalendarService(EventMapper eventMapper,
                                 GoogleCalendarClientFactory calendarClientFactory,
                                 BookingRepository bookingRepository) {
        this.eventMapper = eventMapper;
        this.calendarClientFactory = calendarClientFactory;
        this.bookingRepository = bookingRepository;
    }

    @Cacheable(value = "calendarEvents", key = "#refreshToken")
    public List<Event> getUpcomingEvents(String refreshToken) throws GeneralSecurityException, IOException {
        Calendar calendar = calendarClientFactory.buildClient(refreshToken);

        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Athens"));

        java.time.ZonedDateTime startOfPrevMonth = now.minusMonths(1)
                .with(java.time.temporal.TemporalAdjusters.firstDayOfMonth())
                .with(java.time.LocalTime.MIN);

        java.time.ZonedDateTime endOfNextMonth = now.plusMonths(1)
                .with(java.time.temporal.TemporalAdjusters.lastDayOfMonth())
                .with(java.time.LocalTime.MAX);

        com.google.api.client.util.DateTime timeMin = new com.google.api.client.util.DateTime(java.util.Date.from(startOfPrevMonth.toInstant()));
        com.google.api.client.util.DateTime timeMax = new com.google.api.client.util.DateTime(java.util.Date.from(endOfNextMonth.toInstant()));

        Events events = calendar.events().list("primary")
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setMaxResults(500)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();

        return events.getItems();
    }

    @Transactional
    public String getEventsAsJsonForCalendar(String refreshToken, User user) throws Exception {
        List<com.google.api.services.calendar.model.Event> googleEvents = getUpcomingEvents(refreshToken);
        List<Map<String, Object>> calendarEvents = new ArrayList<>();

        Set<String> processedGoogleEventIds = new HashSet<>();

        for (com.google.api.services.calendar.model.Event gEvent : googleEvents) {
            try {
                processedGoogleEventIds.add(gEvent.getId());

                boolean isDeclined = false;
                if (gEvent.getAttendees() != null) {
                    for (com.google.api.services.calendar.model.EventAttendee attendee : gEvent.getAttendees()) {
                        if (attendee.getEmail() != null && attendee.getEmail().equalsIgnoreCase(user.getEmail())) {
                            if ("declined".equalsIgnoreCase(attendee.getResponseStatus())) {
                                isDeclined = true;
                                break;
                            }
                        }
                    }
                }
                if (isDeclined) {
                    continue;
                }

                com.hua.smartbooking.model.Event entity = eventMapper.googleToEntity(gEvent, user);

                Map<String, Object> map = new HashMap<>();

                String description = gEvent.getDescription();
                Optional<Booking> dbBooking = bookingRepository.findByGoogleEventId(gEvent.getId());
                boolean isSmartBooking = dbBooking.isPresent();

                if (isSmartBooking) {
                    map.put("title", "SmartBooking");
                    map.put("className", "event-smartbooking");
                } else {
                    String typeStr = entity.getType().toString();
                    String shortTitle = typeStr.substring(0, 1).toUpperCase() + typeStr.substring(1).toLowerCase().replace("_", " ");
                    map.put("title", shortTitle);
                    map.put("className", "event-" + entity.getType().toString().toLowerCase());
                }

                map.put("start", entity.getStartTime().toString());
                map.put("end", entity.getEndTime() != null ? entity.getEndTime().toString() : null);

                Map<String, Object> extendedProps = new HashMap<>();
                extendedProps.put("fullTitle", entity.getTitle() != null ? entity.getTitle() : "Untitled Event");

                String roomLoc = entity.getRoom() != null && entity.getRoom().getLocation() != null ? entity.getRoom().getLocation() : null;
                String fallbackLoc = gEvent.getLocation() != null && !gEvent.getLocation().isBlank() ? gEvent.getLocation() : "No location specified";
                extendedProps.put("fullLocation", roomLoc != null ? roomLoc : fallbackLoc);

                extendedProps.put("description", description != null ? description : "No description available.");
                extendedProps.put("type", isSmartBooking ? "SMART_BOOKING" : entity.getType().toString());
                extendedProps.put("locationName", entity.getRoom() != null ? entity.getRoom().getName() : "No location specified");
                extendedProps.put("roomFloor", entity.getRoom() != null ? entity.getRoom().getFloor() : null);
                extendedProps.put("roomImage", entity.getRoom() != null ? entity.getRoom().getImageUrl() : "/images/default-room.jpg");
                extendedProps.put("roomAmenities", entity.getRoom() != null ? entity.getRoom().getAmenities() : new ArrayList<>());

                if (dbBooking.isPresent()) {
                    Map<String, RsvpStatus> decryptedParticipants = new HashMap<>();
                    com.hua.smartbooking.util.StringCryptoConverter crypto = new com.hua.smartbooking.util.StringCryptoConverter();
                    for (Map.Entry<String, RsvpStatus> entry : dbBooking.get().getParticipants().entrySet()) {
                        try {
                            String dec = crypto.convertToEntityAttribute(entry.getKey());
                            decryptedParticipants.put(dec != null ? dec : entry.getKey(), entry.getValue());
                        } catch (Exception e) {
                            decryptedParticipants.put(entry.getKey(), entry.getValue());
                        }
                    }
                    extendedProps.put("participants", decryptedParticipants);
                    extendedProps.put("bookingId", dbBooking.get().getId());
                } else {
                    extendedProps.put("participants", entity.getParticipants());
                }

                map.put("extendedProps", extendedProps);

                calendarEvents.add(map);
            } catch (Exception e) {
                System.err.println("Skipping event due to error: " + gEvent.getSummary() + " -> " + e.getMessage());
            }
        }

        List<Booking> allDbBookings = bookingRepository.findAll();
        com.hua.smartbooking.util.StringCryptoConverter crypto = new com.hua.smartbooking.util.StringCryptoConverter();

        for (Booking dbBooking : allDbBookings) {
            if (dbBooking.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }

            if (dbBooking.getGoogleEventId() != null && processedGoogleEventIds.contains(dbBooking.getGoogleEventId())) {
                continue;
            }

            String organizerRefreshToken = dbBooking.getUser() != null ? dbBooking.getUser().getRefreshToken() : null;
            if (dbBooking.getGoogleEventId() != null && organizerRefreshToken != null
                    && !isEventStillActiveOnGoogle(organizerRefreshToken, dbBooking.getGoogleEventId())) {
                dbBooking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(dbBooking);
                continue;
            }

            Map<String, RsvpStatus> decryptedParticipants = new HashMap<>();
            boolean userIsParticipant = false;
            boolean userDeclined = false;

            for (Map.Entry<String, RsvpStatus> entry : dbBooking.getParticipants().entrySet()) {
                String email = entry.getKey();
                try {
                    String dec = crypto.convertToEntityAttribute(email);
                    email = dec != null ? dec : email;
                } catch (Exception e) {}

                decryptedParticipants.put(email, entry.getValue());

                if (email.equalsIgnoreCase(user.getEmail())) {
                    userIsParticipant = true;
                    if (entry.getValue() == RsvpStatus.DECLINED) {
                        userDeclined = true;
                    }
                }
            }

            if (userIsParticipant && !userDeclined) {
                Map<String, Object> map = new HashMap<>();
                map.put("title", "Smart Meeting");
                map.put("className", "event-smartbooking");
                map.put("start", dbBooking.getStartTime().toString());
                map.put("end", dbBooking.getEndTime() != null ? dbBooking.getEndTime().toString() : null);

                Map<String, Object> extendedProps = new HashMap<>();
                extendedProps.put("fullTitle", dbBooking.getTitle() != null ? dbBooking.getTitle() : "Smart Meeting");

                String roomName = dbBooking.getRoom() != null ? dbBooking.getRoom().getName() : "Room Details";
                String roomLoc = dbBooking.getRoom() != null && dbBooking.getRoom().getLocation() != null
                        ? dbBooking.getRoom().getLocation() : "No location specified";

                extendedProps.put("fullLocation", roomLoc);
                extendedProps.put("description", "Automatically scheduled via SmartBooking App");
                extendedProps.put("type", "SMART_BOOKING");
                extendedProps.put("locationName", roomName);
                extendedProps.put("bookingId", dbBooking.getId());
                extendedProps.put("participants", decryptedParticipants);

                map.put("extendedProps", extendedProps);

                calendarEvents.add(map);
            }
        }

        return new ObjectMapper().writeValueAsString(calendarEvents);
    }

    /**
     * Checks Google Calendar directly for a single event, distinguishing "genuinely deleted"
     * from "just missing from the bulk list fetch" (e.g. Google's anti-spam filtering).
     * Returns false if the event was deleted/cancelled or can no longer be found.
     */
    private boolean isEventStillActiveOnGoogle(String refreshToken, String googleEventId) {
        try {
            Calendar calendar = calendarClientFactory.buildClient(refreshToken);
            Event event = calendar.events().get("primary", googleEventId).execute();
            return event.getStatus() == null || !event.getStatus().equalsIgnoreCase("cancelled");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a new meeting directly on the user's Google Calendar and sends invites.
     */
    public Event createMeetingEvent(String refreshToken, String summary, Instant start, Instant end, String location, List<String> attendeeEmails, String organizerEmail) throws GeneralSecurityException, IOException {

        Event event = new Event()
                .setSummary(summary)
                .setLocation(location)
                .setDescription("Automatically scheduled via SmartBooking App");

        EventDateTime startDateTime = new EventDateTime().setDateTime(new DateTime(start.toEpochMilli()));
        event.setStart(startDateTime);

        EventDateTime endDateTime = new EventDateTime().setDateTime(new DateTime(end.toEpochMilli()));
        event.setEnd(endDateTime);

        if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
            List<EventAttendee> attendees = new ArrayList<>();
            for (String email : attendeeEmails) {
                EventAttendee attendee = new EventAttendee().setEmail(email);

                if (email.equalsIgnoreCase(organizerEmail)) {
                    attendee.setResponseStatus("accepted");
                }

                attendees.add(attendee);
            }
            event.setAttendees(attendees);
        }

        Calendar calendar = calendarClientFactory.buildClient(refreshToken);
        return calendar.events().insert("primary", event).execute();
    }

    /**
     * Updates the RSVP status of a specific attendee directly on Google Calendar.
     */
    public void updateEventRsvpOnGoogleCalendar(String refreshToken, String googleEventId, String userEmail, String responseStatus) throws GeneralSecurityException, IOException {
        Calendar calendar = calendarClientFactory.buildClient(refreshToken);

        Event event = calendar.events().get("primary", googleEventId).execute();

        List<EventAttendee> attendees = event.getAttendees();
        if (attendees != null) {
            boolean updated = false;
            for (EventAttendee attendee : attendees) {
                if (attendee.getEmail().equalsIgnoreCase(userEmail)) {
                    attendee.setResponseStatus(responseStatus);
                    updated = true;
                    break;
                }
            }
            if (updated) {
                calendar.events().update("primary", event.getId(), event).execute();
            }
        }
    }

    public String getAttendeeResponseStatus(String organizerRefreshToken, String googleEventId, String attendeeEmail)
            throws GeneralSecurityException, IOException {

        Calendar calendar = calendarClientFactory.buildClient(organizerRefreshToken);
        Event event = calendar.events().get("primary", googleEventId).execute();

        if (event.getAttendees() == null) {
            return null;
        }

        return event.getAttendees().stream()
                .filter(a -> a.getEmail() != null && a.getEmail().equalsIgnoreCase(attendeeEmail))
                .map(EventAttendee::getResponseStatus)
                .findFirst()
                .orElse(null);
    }


}