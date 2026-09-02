package com.hua.smartbooking.service;

import com.google.api.services.calendar.model.TimePeriod;
import com.hua.smartbooking.dto.TimeSlotScore;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
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
            String organizerEmail,
            ZonedDateTime searchStart,
            ZonedDateTime searchEnd,
            int meetingDurationMinutes,
            List<String> requiredEmails,
            List<String> optionalEmails,
            Map<String, List<TimePeriod>> userBusyBlocks,
            String dailyStartTime,
            String dailyEndTime,
            Integer maxResults
    ) {
        List<TimeSlotScore> viableSlots = new ArrayList<>();
        ZonedDateTime currentSlotStart = searchStart;
        ZoneId athensZone = ZoneId.of("Europe/Athens");

        LocalTime userStartTime = (dailyStartTime != null && !dailyStartTime.trim().isEmpty())
                ? LocalTime.parse(dailyStartTime) : LocalTime.MIN;
        LocalTime userEndTime = (dailyEndTime != null && !dailyEndTime.trim().isEmpty())
                ? LocalTime.parse(dailyEndTime) : LocalTime.MAX;

        while (currentSlotStart.plusMinutes(meetingDurationMinutes).isBefore(searchEnd) ||
                currentSlotStart.plusMinutes(meetingDurationMinutes).isEqual(searchEnd)) {

            ZonedDateTime currentSlotEnd = currentSlotStart.plusMinutes(meetingDurationMinutes);

            if (currentSlotStart.toInstant().isBefore(Instant.now().plus(30, ChronoUnit.MINUTES))) { //minimum notice period
                currentSlotStart = currentSlotStart.plusMinutes(30);
                continue;
            }

            if (isOutsideBusinessHours(currentSlotStart, currentSlotEnd)) {
                currentSlotStart = currentSlotStart.plusMinutes(30);
                continue;
            }

            ZonedDateTime localStart = currentSlotStart.withZoneSameInstant(athensZone);
            ZonedDateTime localEnd = currentSlotEnd.withZoneSameInstant(athensZone);
            if (localStart.toLocalTime().isBefore(userStartTime) || localEnd.toLocalTime().isAfter(userEndTime)) {
                currentSlotStart = currentSlotStart.plusMinutes(30);
                continue;
            }

            if (isUserBusy(organizerEmail, currentSlotStart, currentSlotEnd, userBusyBlocks)) {
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

            int totalParticipants = 1 + requiredEmails.size() + optionalEmails.size();
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

            int totalPeople = 1 + requiredEmails.size() + optionalEmails.size();
            int freePeople = 1 + requiredEmails.size() + optionalAvailable;
            int baseScore = totalPeople > 0 ? (int) Math.round(((double) freePeople / totalPeople) * 100) : 100;

            int penalty = calculateProximityPenalty(organizerEmail, currentSlotStart, currentSlotEnd, userBusyBlocks);

            for (String email : requiredEmails) {
                penalty += calculateProximityPenalty(email, currentSlotStart, currentSlotEnd, userBusyBlocks);
            }

            for (String email : optionalEmails) {
                if (!isUserBusy(email, currentSlotStart, currentSlotEnd, userBusyBlocks)) {
                    penalty += calculateProximityPenalty(email, currentSlotStart, currentSlotEnd, userBusyBlocks);
                }
            }

            double timeOfDayBonus = calculateTimeOfDayScore(currentSlotStart);
            double lunchPenalty = calculateLunchOverlapPenalty(currentSlotStart, currentSlotEnd);
            double soonestBonus = calculateSoonestBonus(currentSlotStart, searchStart, searchEnd);

            double rawScore = baseScore - penalty + timeOfDayBonus - lunchPenalty + soonestBonus;
            int finalScore = (int) Math.round(Math.max(0, rawScore));

            viableSlots.add(new TimeSlotScore(currentSlotStart, currentSlotEnd, finalScore, optionalAvailable));
            currentSlotStart = currentSlotStart.plusMinutes(30);
        }
        int limit = (maxResults != null && maxResults > 0) ? maxResults : 15;

        Collections.sort(viableSlots);
        return viableSlots.stream().limit(limit).toList();
    }

    private boolean isUserBusy(String email, ZonedDateTime slotStart, ZonedDateTime slotEnd, Map<String, List<TimePeriod>> allBusyBlocks) {
        List<TimePeriod> busyBlocks = allBusyBlocks.getOrDefault(email, new ArrayList<>());
        long slotStartMillis = slotStart.toInstant().toEpochMilli();
        long slotEndMillis = slotEnd.toInstant().toEpochMilli();

        long bufferMillis = 15 * 60 * 1000L;

        for (TimePeriod block : busyBlocks) {
            long blockStart = block.getStart().getValue();
            long blockEnd = block.getEnd().getValue();

            long bufferedBlockStart = blockStart - bufferMillis;
            long bufferedBlockEnd = blockEnd + bufferMillis;

            if (slotStartMillis < bufferedBlockEnd && slotEndMillis > bufferedBlockStart) {
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

        int startHour = localStart.getHour();
        int endHour = localEnd.getHour();

        if (startHour < 8 || startHour >= 20) return true;

        if (endHour > 20 || (endHour == 20 && localEnd.getMinute() > 0)) return true;

        if (endHour < startHour) return true;

        return false;
    }

    private int calculateProximityPenalty(String email, ZonedDateTime slotStart, ZonedDateTime slotEnd, Map<String, List<TimePeriod>> allBusyBlocks) {
        List<TimePeriod> busyBlocks = allBusyBlocks.getOrDefault(email, new ArrayList<>());
        long slotStartMillis = slotStart.toInstant().toEpochMilli();
        long slotEndMillis = slotEnd.toInstant().toEpochMilli();

        long bufferMillis = 30 * 60 * 1000L;

        int penalty = 0;
        for (TimePeriod block : busyBlocks) {
            long blockStart = block.getStart().getValue();
            long blockEnd = block.getEnd().getValue();

            long gapBefore = slotStartMillis - blockEnd;
            if (gapBefore >= 0 && gapBefore <= bufferMillis) {
                penalty += gradedProximityPenalty(gapBefore, bufferMillis);
            }

            long gapAfter = blockStart - slotEndMillis;
            if (gapAfter >= 0 && gapAfter <= bufferMillis) {
                penalty += gradedProximityPenalty(gapAfter, bufferMillis);
            }
        }
        return penalty;
    }

    // The closer the slot is to an existing busy block, the higher the penalty it gets (linear scale).
    private double gradedProximityPenalty(long gapMillis, long bufferMillis) {
        double fraction = 1.0 - ((double) gapMillis / bufferMillis);
        return fraction * 20;
    }

    // Continuous preference curve peaked around 11:00 and 15:00 — every 30-min slot
    // gets a distinct value based on its distance from the nearest peak, instead of
    // a handful of flat buckets that make separate slots tie on score.
    private double calculateTimeOfDayScore(ZonedDateTime start) {
        ZoneId athensZone = ZoneId.of("Europe/Athens");
        ZonedDateTime local = start.withZoneSameInstant(athensZone);
        double hourFraction = local.getHour() + local.getMinute() / 60.0;

        double distanceToMorningPeak = Math.abs(hourFraction - 11.0);
        double distanceToAfternoonPeak = Math.abs(hourFraction - 15.0);
        double distanceToNearestPeak = Math.min(distanceToMorningPeak, distanceToAfternoonPeak);

        return Math.max(-30, 30 - (distanceToNearestPeak * 8));
    }

    // Penalizes slots proportionally to how much of the meeting overlaps the classic
    // lunch break (12:00-14:00) , a 5-minute overlap is barely penalized, a fully
    // contained lunch meeting gets the full penalty, instead of one flat number for any overlap.
    private double calculateLunchOverlapPenalty(ZonedDateTime start, ZonedDateTime end) {
        ZoneId athensZone = ZoneId.of("Europe/Athens");
        ZonedDateTime localStart = start.withZoneSameInstant(athensZone);
        ZonedDateTime localEnd = end.withZoneSameInstant(athensZone);

        ZonedDateTime lunchStart = localStart.withHour(12).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime lunchEnd = localStart.withHour(14).withMinute(0).withSecond(0).withNano(0);

        long overlapSeconds = Math.max(0,
                Math.min(localEnd.toEpochSecond(), lunchEnd.toEpochSecond())
                        - Math.max(localStart.toEpochSecond(), lunchStart.toEpochSecond()));

        long meetingSeconds = Math.max(1, Duration.between(localStart, localEnd).getSeconds());
        double overlapFraction = Math.min(1.0, (double) overlapSeconds / meetingSeconds);

        // Squared instead of linear: a 25% overlap is only lightly penalized, while a
        // meeting fully inside lunch still gets the full penalty. This keeps a slot that
        // barely dips into lunch from losing to a slot with no "prime hour" bonus at all.
        return (overlapFraction * overlapFraction) * 40;
    }

    // Gives a small tie-breaker bonus to the slots closest to the start of the requested date range.
    private double calculateSoonestBonus(ZonedDateTime slotStart, ZonedDateTime searchStart, ZonedDateTime searchEnd) {
        long totalRangeMinutes = Duration.between(searchStart, searchEnd).toMinutes();
        if (totalRangeMinutes <= 0) {
            return 0;
        }
        long minutesFromStart = Duration.between(searchStart, slotStart).toMinutes();
        double fraction = 1.0 - ((double) minutesFromStart / totalRangeMinutes);
        return Math.max(0, fraction) * 20;
    }
}