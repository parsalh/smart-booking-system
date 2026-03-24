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
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.mapper.EventMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ObjectMapper objectMapper;

    public GoogleCalendarService(EventMapper eventMapper) {
        this.eventMapper = eventMapper;
        this.objectMapper = new ObjectMapper();
    }

    public List<Event> getUpcomingEvents(String refreshToken) throws Exception {
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

        Calendar service = new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("SmartBooking")
                .build();

        java.time.ZonedDateTime thirtyDaysAgo = java.time.ZonedDateTime.now().minusDays(30);
        DateTime pastDate = new DateTime(thirtyDaysAgo.toInstant().toEpochMilli());

        Events events = service.events().list("primary")
                .setMaxResults(50)
                .setTimeMin(pastDate)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();

        return events.getItems() != null ? events.getItems() : new ArrayList<>();
    }

    @Transactional
    public String getEventsAsJsonForCalendar(String refreshToken, User user) throws Exception {
        List<com.google.api.services.calendar.model.Event> googleEvents = getUpcomingEvents(refreshToken);
        List<Map<String, Object>> calendarEvents = new ArrayList<>();

        for (com.google.api.services.calendar.model.Event gEvent : googleEvents) {
            try {
                com.hua.smartbooking.model.Event entity = eventMapper.googleToEntity(gEvent, user);

                Map<String, Object> map = new HashMap<>();

                String typeStr = entity.getType().toString();
                String shortTitle = typeStr.substring(0, 1).toUpperCase() + typeStr.substring(1).toLowerCase().replace("_", " ");
                map.put("title", shortTitle);

                map.put("start", entity.getStartTime().toString());
                map.put("end", entity.getEndTime() != null ? entity.getEndTime().toString() : null);

                map.put("className", "event-" + entity.getType().toString().toLowerCase());

                Map<String, Object> extendedProps = new HashMap<>();

                extendedProps.put("fullTitle", entity.getTitle() != null ? entity.getTitle() : "Untitled Event");
                extendedProps.put("fullLocation", gEvent.getLocation() != null ? gEvent.getLocation() : "No location specified");
                extendedProps.put("description", gEvent.getDescription() != null ? gEvent.getDescription() : "No description available.");
                extendedProps.put("type", entity.getType().toString());
                extendedProps.put("locationName", entity.getRoom() != null ? entity.getRoom().getName() : "No location specified");
                extendedProps.put("roomFloor", entity.getRoom() != null ? entity.getRoom().getFloor() : null);
                extendedProps.put("roomImage", entity.getRoom() != null ? entity.getRoom().getImageUrl() : "/images/default-room.jpg");
                extendedProps.put("roomAmenities", entity.getRoom() != null ? entity.getRoom().getAmenities() : new ArrayList<>());

                map.put("extendedProps", extendedProps);

                calendarEvents.add(map);
            } catch (Exception e) {
                System.err.println("Skipping event due to error: " + gEvent.getSummary() + " -> " + e.getMessage());
            }
        }
        return new ObjectMapper().writeValueAsString(calendarEvents);
    }

    public int getEventCount(String refreshToken) throws Exception {
        List<Event> events = getUpcomingEvents(refreshToken);
        return events != null ? events.size() : 0;
    }

    /**
     * Maps a Google Event to internal JPA Entity.
     */
    public com.hua.smartbooking.model.Event convertToEntity(Event googleEvent, User user) {
        com.hua.smartbooking.model.Event myEvent = new com.hua.smartbooking.model.Event();

        myEvent.setTitle(googleEvent.getSummary());
        myEvent.setUser(user);

        if (googleEvent.getStart().getDateTime() != null) {
            String rfcDate = googleEvent.getStart().getDateTime().toStringRfc3339().substring(0, 19);
            myEvent.setStartTime(LocalDateTime.parse(rfcDate));
        } else {
            myEvent.setStartTime(LocalDateTime.parse(googleEvent.getStart().getDate().toString() + "T00:00:00"));
        }

        String summary = (googleEvent.getSummary() != null) ? googleEvent.getSummary().toLowerCase() : "";

        if (summary.contains("lecture")) {
            myEvent.setType(com.hua.smartbooking.model.Event.EventType.LECTURE);
        } else if (summary.contains("lab")) {
            myEvent.setType(com.hua.smartbooking.model.Event.EventType.LAB);
        } else if (summary.contains("office")) {
            myEvent.setType(com.hua.smartbooking.model.Event.EventType.OFFICE_HOURS);
        } else if (summary.contains("meeting")) {
            myEvent.setType(com.hua.smartbooking.model.Event.EventType.MEETING);
        } else {
            myEvent.setType(com.hua.smartbooking.model.Event.EventType.OTHER);
        }

        return myEvent;
    }

    public long getMeetingCount(String refreshToken, User user) throws Exception {
        List<Event> googleEvents = getUpcomingEvents(refreshToken);
        if (googleEvents == null) return 0;

        return googleEvents.stream()
                .map(gEvent -> convertToEntity(gEvent, user))
                .filter(myEvent -> myEvent.getType() == com.hua.smartbooking.model.Event.EventType.MEETING)
                .count();
    }

}