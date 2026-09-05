package com.hua.smartbooking.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.TimePeriod;
import com.hua.smartbooking.dto.TimeSlotScore;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeetingOptimizerServiceTest {

    private RoomRepository roomRepository;
    private MeetingOptimizerService optimizerService;
    private ZonedDateTime searchStart;
    private ZonedDateTime searchEnd;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        optimizerService = new MeetingOptimizerService(roomRepository);

        Room fakeRoom = new Room();
        fakeRoom.setId(1L);
        fakeRoom.setCapacity(10);
        when(roomRepository.findAvailableRooms(anyInt(), any(), any())).thenReturn(List.of(fakeRoom));

        // Use the next Monday, 09:00-18:00 Athens time, so weekday/business-hours
        // filtering in the optimizer never excludes the whole window and results
        // stay deterministic regardless of when the test suite actually runs.
        ZoneId athens = ZoneId.of("Europe/Athens");
        ZonedDateTime nextMonday = ZonedDateTime.now(athens)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(9).withMinute(0).withSecond(0).withNano(0);

        searchStart = nextMonday;
        searchEnd = nextMonday.withHour(18);
    }

    @Test
    void returnsSlotsWhenEveryoneIsFree() {
        List<TimeSlotScore> slots = optimizerService.findBestTimeSlots(
                "organizer@hua.gr",
                searchStart, searchEnd,
                30,
                List.of(), List.of(),
                Map.of(),
                null, null, 15
        );

        assertThat(slots).isNotEmpty();
    }

    @Test
    void excludesSlotsWhenRequiredParticipantIsBusyTheEntireWindow() {
        TimePeriod allDayBusy = new TimePeriod()
                .setStart(new DateTime(searchStart.toInstant().toEpochMilli()))
                .setEnd(new DateTime(searchEnd.toInstant().toEpochMilli()));

        Map<String, List<TimePeriod>> busyBlocks = Map.of("busy@hua.gr", List.of(allDayBusy));

        List<TimeSlotScore> slots = optimizerService.findBestTimeSlots(
                "organizer@hua.gr",
                searchStart, searchEnd,
                30,
                List.of("busy@hua.gr"), List.of(),
                busyBlocks,
                null, null, 15
        );

        assertThat(slots).isEmpty();
    }

    @Test
    void returnsNoSlotsWhenNoRoomHasCapacity() {
        when(roomRepository.findAvailableRooms(anyInt(), any(), any())).thenReturn(List.of());

        List<TimeSlotScore> slots = optimizerService.findBestTimeSlots(
                "organizer@hua.gr",
                searchStart, searchEnd,
                30,
                List.of(), List.of(),
                Map.of(),
                null, null, 15
        );

        assertThat(slots).isEmpty();
    }

    @Test
    void slotsAreSortedByScoreDescending() {
        List<TimeSlotScore> slots = optimizerService.findBestTimeSlots(
                "organizer@hua.gr",
                searchStart, searchEnd,
                30,
                List.of(), List.of(),
                Map.of(),
                null, null, 50
        );

        assertThat(slots).isNotEmpty();
        for (int i = 0; i < slots.size() - 1; i++) {
            assertThat(slots.get(i).score()).isGreaterThanOrEqualTo(slots.get(i + 1).score());
        }
    }

    @Test
    void slotOverlappingLunchScoresLowerThanEquivalentMorningSlot() {
        List<TimeSlotScore> slots = optimizerService.findBestTimeSlots(
                "organizer@hua.gr",
                searchStart, searchEnd,
                30,
                List.of(), List.of(),
                Map.of(),
                null, null, 100
        );

        ZonedDateTime lunchSlotStart = searchStart.withHour(12).withMinute(30);
        ZonedDateTime morningSlotStart = searchStart.withHour(9).withMinute(0);

        TimeSlotScore lunchSlot = slots.stream()
                .filter(s -> s.startTime().equals(lunchSlotStart))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a 12:30 slot in results"));
        TimeSlotScore morningSlot = slots.stream()
                .filter(s -> s.startTime().equals(morningSlotStart))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a 09:00 slot in results"));

        assertThat(lunchSlot.score()).isLessThan(morningSlot.score());
    }

    @Test
    void respectsMaxResultsLimit() {
        List<TimeSlotScore> slots = optimizerService.findBestTimeSlots(
                "organizer@hua.gr",
                searchStart, searchEnd,
                30,
                List.of(), List.of(),
                Map.of(),
                null, null, 3
        );

        assertThat(slots).hasSizeLessThanOrEqualTo(3);
    }
}