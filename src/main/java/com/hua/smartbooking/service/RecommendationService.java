package com.hua.smartbooking.service;

import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.BookingRepository;
import com.hua.smartbooking.repository.EventRepository;
import com.hua.smartbooking.repository.RoomRepository;
import com.hua.smartbooking.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecommendationService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public RecommendationService(EventRepository eventRepository,
                                 BookingRepository bookingRepository,
                                 RoomRepository roomRepository,
                                 UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    public List<LocalDateTime> suggestAvailableSlots(int durationHours,
                                                     int searchDays,
                                                     int minCapacity,
                                                     List<String> participantEmails) {

        List<LocalDateTime> recommendedSlots = new ArrayList<>();
        ZoneId athensZone = ZoneId.of("Europe/Athens");

        List<User> participants = userRepository.findAll().stream()
                .filter(u -> participantEmails.contains(u.getEmail()))
                .toList();

        List<Event> allParticipantEvents = new ArrayList<>();
        for (User user : participants) {
            allParticipantEvents.addAll(eventRepository.findByUser(user));
        }

        List<Room> candidateRooms = roomRepository.findAll().stream()
                .filter(r -> r.getCapacity() >= minCapacity)
                .toList();

        if (candidateRooms.isEmpty()) {
            return recommendedSlots;
        }

        LocalDate startDate = LocalDate.now(athensZone).plusDays(1);

        for (int day = 0; day < searchDays; day++) {
            LocalDate currentDay = startDate.plusDays(day);

            if (currentDay.getDayOfWeek() == DayOfWeek.SATURDAY || currentDay.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            for (int hour = 9; hour <= (17 - durationHours); hour++) {
                LocalDateTime proposedStart = currentDay.atTime(hour, 0);
                LocalDateTime proposedEnd = proposedStart.plusHours(durationHours);

                boolean allParticipantsFree = true;
                for (Event ev : allParticipantEvents) {
                    if (ev.getStartTime() != null && ev.getEndTime() != null) {
                        if (!isSlotAvailable(proposedStart, proposedEnd, ev.getStartTime(), ev.getEndTime())) {
                            allParticipantsFree = false;
                            break;
                        }
                    }
                }

                if (allParticipantsFree) {
                    boolean atLeastOneRoomFree = false;
                    Instant startInstant = proposedStart.atZone(athensZone).toInstant();
                    Instant endInstant = proposedEnd.atZone(athensZone).toInstant();

                    for (Room room : candidateRooms) {
                        if (!bookingRepository.hasConflictingBookings(room.getId(), startInstant, endInstant)) {
                            atLeastOneRoomFree = true;
                            break;
                        }
                    }

                    if (atLeastOneRoomFree) {
                        recommendedSlots.add(proposedStart);
                    }
                }

                if (recommendedSlots.size() >= 10) {
                    return recommendedSlots;
                }
            }
        }

        return recommendedSlots;
    }

    private boolean isSlotAvailable(LocalDateTime proposedStart, LocalDateTime proposedEnd, Instant eventStart, Instant eventEnd) {
        ZoneId athensZone = ZoneId.of("Europe/Athens");

        Instant slotStartUtc = proposedStart.atZone(athensZone).toInstant();
        Instant slotEndUtc = proposedEnd.atZone(athensZone).toInstant();

        boolean hasOverlap = slotStartUtc.isBefore(eventEnd) && slotEndUtc.isAfter(eventStart);

        return !hasOverlap;
    }
}