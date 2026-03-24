package com.hua.smartbooking.mapper;

import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
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

        if (gEvent.getStart().getDateTime() != null) {
            entity.setStartTime(LocalDateTime.parse(gEvent.getStart().getDateTime().toStringRfc3339().substring(0, 19)));
        } else {
            entity.setStartTime(LocalDateTime.parse(gEvent.getStart().getDate().toString() + "T00:00:00"));
        }

        if (gEvent.getEnd() != null) {
            if (gEvent.getEnd().getDateTime() != null) {
                entity.setEndTime(LocalDateTime.parse(gEvent.getEnd().getDateTime().toStringRfc3339().substring(0, 19)));
            } else {
                entity.setEndTime(LocalDateTime.parse(gEvent.getEnd().getDate().toString() + "T23:59:59"));
            }
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

            Room room = roomRepository.findByNameIgnoreCase(searchName)
                    .orElseGet(() -> {
                        Room newRoom = roomMapper.mapLocationToEntity(searchName);
                        if (finalBuilding != null) newRoom.setLocation(finalBuilding);

                        if (finalFloor != null) newRoom.setFloor(finalFloor);

                        return roomRepository.saveAndFlush(newRoom);
                    });

            entity.setRoom(room);
        } else {
            entity.setRoom(null);
        }

        entity.setType(classify(gEvent.getSummary()));

        return entity;
    }

    private Event.EventType classify(String summary) {
        if (summary == null || summary.isEmpty()) return Event.EventType.OTHER;
        String s = summary.toLowerCase();
        if (s.contains("lecture")) return Event.EventType.LECTURE;
        if (s.contains("lab")) return Event.EventType.LAB;
        if (s.contains("office")) return Event.EventType.OFFICE_HOURS;
        if (s.contains("meeting")) return Event.EventType.MEETING;
        return Event.EventType.OTHER;
    }

}
