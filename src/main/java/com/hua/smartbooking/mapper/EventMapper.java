package com.hua.smartbooking.mapper;

import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.nlp.EventTypeClassifier;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class EventMapper {

    private final RoomRepository roomRepository;
    private final EventTypeClassifier eventTypeClassifier;

    public EventMapper(RoomRepository roomRepository, EventTypeClassifier eventTypeClassifier) {
        this.roomRepository = roomRepository;
        this.eventTypeClassifier = eventTypeClassifier;
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
            String searchName = rawGoogleLocation.trim().replaceAll("\\s+", " ");
            Room room = roomRepository.findByNameIgnoreCase(searchName).orElse(null);
            entity.setRoom(room);
        } else {
            entity.setRoom(null);
        }

        entity.setType(eventTypeClassifier.classify(gEvent.getSummary()));

        return entity;
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