package com.hua.smartbooking.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter for the invite-by-email feature. Prevents:
 *  1. Spamming the same target email with repeated invites (24h cooldown per email).
 *  2. A single organizer mass-inviting arbitrary emails (cap per organizer per day).
 *
 * @author Stavroula Parsali
 */
@Service
public class InviteRateLimiterService {

    private static final Duration COOLDOWN_PER_EMAIL = Duration.ofHours(24);
    private static final Duration ORGANIZER_WINDOW = Duration.ofDays(1);
    private static final int MAX_INVITES_PER_ORGANIZER_PER_WINDOW = 20;

    private final Map<String, Instant> lastInviteToEmail = new ConcurrentHashMap<>();
    private final Map<String, OrganizerUsage> organizerUsage = new ConcurrentHashMap<>();

    private static class OrganizerUsage {
        int count;
        Instant windowStart;
    }

    public synchronized String checkAndRecord(String organizerEmail, String targetEmail) {
        Instant now = Instant.now();
        String normalizedTarget = targetEmail.toLowerCase().trim();
        String normalizedOrganizer = organizerEmail.toLowerCase().trim();

        Instant lastSent = lastInviteToEmail.get(normalizedTarget);
        if (lastSent != null && Duration.between(lastSent, now).compareTo(COOLDOWN_PER_EMAIL) < 0) {
            return "An invite was already sent to this email recently. Please wait before sending another.";
        }

        OrganizerUsage usage = organizerUsage.computeIfAbsent(normalizedOrganizer, k -> {
            OrganizerUsage u = new OrganizerUsage();
            u.windowStart = now;
            u.count = 0;
            return u;
        });

        if (Duration.between(usage.windowStart, now).compareTo(ORGANIZER_WINDOW) >= 0) {
            usage.windowStart = now;
            usage.count = 0;
        }

        if (usage.count >= MAX_INVITES_PER_ORGANIZER_PER_WINDOW) {
            return "You've sent a lot of invites today. Please try again tomorrow.";
        }

        lastInviteToEmail.put(normalizedTarget, now);
        usage.count++;
        return null;
    }
}