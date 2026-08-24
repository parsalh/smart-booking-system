package com.hua.smartbooking.mapper;

import com.hua.smartbooking.enums.EventType;
import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
public class EventMapper {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;
    private final RestTemplate restTemplate;

    public EventMapper(RoomRepository roomRepository,
                       RoomMapper roomMapper,
                       RestTemplate restTemplate) {
        this.roomRepository = roomRepository;
        this.roomMapper = roomMapper;
        this.restTemplate = restTemplate;
    }

    public Event googleToEntity(com.google.api.services.calendar.model.Event gEvent, User user) {

        Event entity = new Event();
        entity.setGoogleEventId(gEvent.getId());
        entity.setTitle(gEvent.getSummary());
        entity.setUser(user);

        entity.setStartTime(convertGoogleTimeToInstant(gEvent.getStart()));
        entity.setEndTime(convertGoogleTimeToInstant(gEvent.getEnd()));

        if (gEvent.getAttendees() != null) {
            List<String> attendeeEmails = gEvent.getAttendees().stream()
                    .map(com.google.api.services.calendar.model.EventAttendee::getEmail)
                    .filter(email -> email != null && !email.isEmpty())
                    .toList();
            entity.setParticipants(attendeeEmails);
        }

        String rawGoogleLocation = gEvent.getLocation();

        if (rawGoogleLocation != null && !rawGoogleLocation.isEmpty()) {

            String roomName = rawGoogleLocation.trim();
            String buildingName = null;
            String floorNum = null;

            try {
                String pythonApiUrl = "http://host.docker.internal:8000/parse-location";
                Map<String, String> requestPayload = Map.of("raw_text", rawGoogleLocation);

                ResponseEntity<Map> response = restTemplate.postForEntity(pythonApiUrl, requestPayload, Map.class);

                @SuppressWarnings("unchecked")
                Map<String, Object> nlpResult = (Map<String, Object>) response.getBody();

                if (nlpResult != null) {
                    if (nlpResult.get("room") != null) roomName = (String) nlpResult.get("room");
                    if (nlpResult.get("building") != null) buildingName = (String) nlpResult.get("building");

                    if (nlpResult.get("floor") != null) {
                        floorNum = String.valueOf(nlpResult.get("floor"));
                    }
                }
            } catch (Exception e) {
                System.err.println("NLP Service error: " + e.getMessage());
                System.out.println("Falling back to basic parsing.");
            }

            final String searchName = roomName.replaceAll("\\s+", " ");
            final String finalBuilding = buildingName;
            final String finalFloor = floorNum;

            Room room = roomRepository.findByNameIgnoreCase(searchName).orElse(null);
            entity.setRoom(room);
        } else {
            entity.setRoom(null);
        }

        entity.setType(classify(gEvent.getSummary()));

        return entity;
    }

    private EventType classify(String summary) {
        if (summary == null || summary.isEmpty()) return EventType.OTHER;
        String s = summary.toLowerCase();
        if (s.contains("lecture")) return EventType.LECTURE;
        if (s.contains("lab")) return EventType.LAB;
        if (s.contains("office")) return EventType.OFFICE_HOURS;
        if (s.contains("meeting")) return EventType.MEETING;
        return EventType.OTHER;
    }

    private Instant convertGoogleTimeToInstant(com.google.api.services.calendar.model.EventDateTime googleTime) {
        if (googleTime == null) return null;

        if (googleTime.getDateTime() != null) {
            return Instant.ofEpochMilli(googleTime.getDateTime().getValue());
        }

        if (googleTime.getDate() != null) {
            return LocalDate.parse(googleTime.getDate().toString())
                    .atStartOfDay(ZoneId.of("Europe/Athens"))
                    .toInstant();
        }

        return null;
    }


}
