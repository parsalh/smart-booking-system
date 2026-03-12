package com.hua.smartbooking.service;

import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.mapper.EventMapper;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventMappingService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public EventMappingService(EventRepository eventRepository,
                               EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public void syncEvents(List<com.google.api.services.calendar.model.Event> googleEvents, User user) {
        if (googleEvents == null) return;

        for (com.google.api.services.calendar.model.Event gEvent : googleEvents) {
            if (!eventRepository.existsByGoogleEventId(gEvent.getId())) {
                Event entity = eventMapper.googleToEntity(gEvent, user);
                eventRepository.save(entity);
            }
        }
    }

    public List<Event> getUserEvents(User user) {
        return eventRepository.findByUser(user);
    }

    public long countMeetingsForUser(User user) {
        return eventRepository.findByUser(user).stream()
                .filter(e -> e.getType() == Event.EventType.MEETING)
                .count();
    }

}
