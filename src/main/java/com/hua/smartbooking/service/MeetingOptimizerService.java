package com.hua.smartbooking.service;

import com.google.api.services.calendar.model.TimePeriod;
import com.hua.smartbooking.dto.TimeSlotScore;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.ZoneId;
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
                    currentSlotStart.toInstant(),
                    currentSlotEnd.toInstant()
            );

            if (availableRooms.isEmpty()) {
                currentSlotStart = currentSlotStart.plusMinutes(30);
                continue;
            }

            int optionalAvailable = 0;
            for (String email : optionalEmails) {
                if (!isUserBusy(email, currentSlotStart, currentSlotEnd, userBusyBlocks)) {
                    optionalAvailable++;
                }
            }

            int totalPeople = requiredEmails.size() + optionalEmails.size();
            int freePeople = requiredEmails.size() + optionalAvailable;
            int baseScore = totalPeople > 0 ? (int) Math.round(((double) freePeople / totalPeople) * 100) : 100;

            int penalty = 0;

            for (String email : requiredEmails) {
                penalty += calculateProximityPenalty(email, currentSlotStart, currentSlotEnd, userBusyBlocks);
            }

            for (String email : optionalEmails) {
                if (!isUserBusy(email, currentSlotStart, currentSlotEnd, userBusyBlocks)) {
                    penalty += calculateProximityPenalty(email, currentSlotStart, currentSlotEnd, userBusyBlocks);
                }
            }

            int finalScore = Math.max(0, baseScore - penalty);

            viableSlots.add(new TimeSlotScore(currentSlotStart, currentSlotEnd, finalScore, optionalAvailable));
            currentSlotStart = currentSlotStart.plusMinutes(30);
        }

        Collections.sort(viableSlots);

        return viableSlots.stream().limit(15).toList();
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
        ZoneId athensZone = ZoneId.of("Europe/Athens");
        ZonedDateTime localStart = start.withZoneSameInstant(athensZone);
        ZonedDateTime localEnd = end.withZoneSameInstant(athensZone);

        if (localStart.getDayOfWeek() == DayOfWeek.SATURDAY || localStart.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }
        return localStart.getHour() < 8 || localEnd.getHour() > 20 || (localEnd.getHour() == 20 && localEnd.getMinute() > 0);
    }

    private int calculateProximityPenalty(String email, ZonedDateTime slotStart, ZonedDateTime slotEnd, Map<String, List<TimePeriod>> allBusyBlocks) {
        List<TimePeriod> busyBlocks = allBusyBlocks.getOrDefault(email, new ArrayList<>());
        long slotStartMillis = slotStart.toInstant().toEpochMilli();
        long slotEndMillis = slotEnd.toInstant().toEpochMilli();

        long bufferMillis = 60 * 60 * 1000L;

        int penalty = 0;
        for (TimePeriod block : busyBlocks) {
            long blockStart = block.getStart().getValue();
            long blockEnd = block.getEnd().getValue();

            if (blockEnd <= slotStartMillis && (slotStartMillis - blockEnd) <= bufferMillis) {
                penalty += 10; // Αφαιρούμε 10 πόντους
            }
            if (blockStart >= slotEndMillis && (blockStart - slotEndMillis) <= bufferMillis) {
                penalty += 10; // Αφαιρούμε 10 πόντους
            }
        }
        return penalty;
    }
}
