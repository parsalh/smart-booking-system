package com.hua.smartbooking.mapper;

import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class EventMapper {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;

    public EventMapper(RoomRepository roomRepository,
                       RoomMapper roomMapper) {
        this.roomRepository = roomRepository;
        this.roomMapper = roomMapper;
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

        String locationName = gEvent.getLocation();
        if (locationName != null && !locationName.isEmpty()) {
            String cleanName = locationName.trim().toLowerCase();
            Room room = roomRepository.findByName(cleanName)
                    .orElseGet(() -> {
                        Room newRoom = roomMapper.mapLocationToEntity(locationName);
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
