package com.hua.smartbooking.dto;

import java.time.ZonedDateTime;

/**
 * A data transfer object representing a potential meeting interval.
 */
public record TimeSlotScore(
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        int score,
        int optionalAttendeesAvailable
) implements Comparable<TimeSlotScore> {

    @Override
    public int compareTo(TimeSlotScore other) {
        return Integer.compare(other.score, this.score);
    }
}
