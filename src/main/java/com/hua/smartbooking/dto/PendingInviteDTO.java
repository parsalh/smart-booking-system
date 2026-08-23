package com.hua.smartbooking.dto;

import java.time.Instant;

public record PendingInviteDTO(
        Long bookingId,
        String title,
        String organizerName,
        String organizerEmail,
        String roomName,
        Instant startTime,
        Instant endTime
) {
}