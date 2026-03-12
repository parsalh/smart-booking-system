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

        DateTime now = new DateTime(System.currentTimeMillis());

        Events events = service.events().list("primary")
                .setMaxResults(15)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();

        return events.getItems() != null ? events.getItems() : new ArrayList<>();
    }

    public String getEventsAsJsonForCalendar(String refreshToken, User user) throws Exception {
        List<com.google.api.services.calendar.model.Event> googleEvents = getUpcomingEvents(refreshToken);
        List<Map<String, Object>> calendarEvents = new ArrayList<>();

        for (com.google.api.services.calendar.model.Event gEvent : googleEvents) {

            com.hua.smartbooking.model.Event entity = eventMapper.googleToEntity(gEvent, user);

            Map<String, Object> map = new HashMap<>();
            map.put("title", entity.getTitle());
            map.put("start", entity.getStartTime().toString());
            map.put("end", entity.getEndTime() != null ? entity.getEndTime().toString() : null);
            map.put("description", gEvent.getDescription() != null ? gEvent.getDescription() : "No description available.");

            map.put("locationName", entity.getRoom() != null ? entity.getRoom().getName() : null);

            String color = switch (entity.getType()) {
                case LECTURE -> "#2563eb";
                case LAB -> "#dc2626";
                case MEETING -> "#059669";
                case OFFICE_HOURS -> "#7c3aed";
                default -> "#4b5563";
            };
            map.put("color", color);
            map.put("textColor", "#ffffff");

            calendarEvents.add(map);
        }
        return new ObjectMapper().writeValueAsString(calendarEvents);
    }

    public int getEventCount(String refreshToken) throws Exception {
        List<Event> events = getUpcomingEvents(refreshToken);
        return events != null ? events.size() : 0;
    }

    /**
     * Maps a Google Event to our internal JPA Entity.
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