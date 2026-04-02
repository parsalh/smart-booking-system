package com.hua.smartbooking.service;

import com.google.api.services.calendar.model.TimePeriod;
import com.hua.smartbooking.dto.TimeSlotScore;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for calculating the optimal meeting times.
 */
@Service
public class MeetingOptimizerService {

    private final RoomRepository roomRepository;

    public MeetingOptimizerService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<TimeSlotScore> findBestTimeSlots(
            ZonedDateTime searchStart,
            ZonedDateTime searchEnd,
            int meetingDurationMinutes,
            List<String> requiredEmails,
            List<String> optionalEmails,
            Map<String, List<TimePeriod>> userBusyBlocks
    ) {
        List<TimeSlotScore> viableSlots = new ArrayList<>();

        ZonedDateTime currentSlotStart = searchStart;

        while (currentSlotStart.plusMinutes(meetingDurationMinutes).isBefore(searchEnd) ||
                currentSlotStart.plusMinutes(meetingDurationMinutes).isEqual(searchEnd)) {

            ZonedDateTime currentSlotEnd = currentSlotStart.plusMinutes(meetingDurationMinutes);

            if (isOutsideBusinessHours(currentSlotStart, currentSlotEnd)) {
                currentSlotStart = currentSlotStart.plusMinutes(30);
                continue;
            }

            boolean requiredAreFree = true;
            for (String email : requiredEmails) {
                if (isUserBusy(email, currentSlotStart, currentSlotEnd, userBusyBlocks)) {
                    requiredAreFree = false;
                    break;
                }
            }

            if (!requiredAreFree) {
                currentSlotStart = currentSlotStart.plusMinutes(30);
                continue;
            }

            int totalParticipants = requiredEmails.size() + optionalEmails.size();
            List<Room> availableRooms = roomRepository.findAvailableRooms(
                    totalParticipants,
                    currentSlotStart.toLocalDateTime(),
                    currentSlotEnd.toLocalDateTime()
            );

            if (availableRooms.isEmpty()) {
                currentSlotStart = currentSlotStart.plusMinutes(30);
                continue;
            }

            int score = 100;
            int optionalAvailable = 0;

            for (String email : optionalEmails) {
                if (!isUserBusy(email, currentSlotStart, currentSlotEnd, userBusyBlocks)) {
                    optionalAvailable++;
                    score += 20;
                }
            }

            viableSlots.add(new TimeSlotScore(currentSlotStart, currentSlotEnd, score, optionalAvailable));

            currentSlotStart = currentSlotStart.plusMinutes(30);
        }

        Collections.sort(viableSlots);

        return viableSlots;
    }

    private boolean isUserBusy(String email, ZonedDateTime slotStart, ZonedDateTime slotEnd, Map<String, List<TimePeriod>> allBusyBlocks) {
        List<TimePeriod> busyBlocks = allBusyBlocks.getOrDefault(email, new ArrayList<>());
        long slotStartMillis = slotStart.toInstant().toEpochMilli();
        long slotEndMillis = slotEnd.toInstant().toEpochMilli();

        for (TimePeriod block : busyBlocks) {
            long blockStart = block.getStart().getValue();
            long blockEnd = block.getEnd().getValue();

            if (slotStartMillis < blockEnd && slotEndMillis > blockStart) {
                return true;
            }
        }
        return false;
    }

    private boolean isOutsideBusinessHours(ZonedDateTime start, ZonedDateTime end) {
        if (start.getDayOfWeek() == DayOfWeek.SATURDAY || start.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }
        return start.getHour() < 8 || end.getHour() > 20 || (end.getHour() == 20 && end.getMinute() > 0);
    }
}
