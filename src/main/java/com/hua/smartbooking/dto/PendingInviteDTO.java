package com.hua.smartbooking.dto;

import java.time.Instant;

/**
 * Read-only projection of a meeting invite the current user hasn't
 * responded to yet, returned by GET /api/bookings/pending-invites:
 * the booking id, meeting title, organizer's name and email, room
 * name, and start/end time.
 *
 * @author Stavroula Parsali
 */
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