package com.hua.smartbooking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InviteRateLimiterServiceTest {

    private InviteRateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new InviteRateLimiterService();
    }

    @Test
    void firstInviteToNewEmailIsAllowed() {
        String result = rateLimiter.checkAndRecord("organizer@hua.gr", "guest@example.com");

        assertThat(result).isNull();
    }

    @Test
    void secondInviteToSameEmailWithin24hIsBlocked() {
        rateLimiter.checkAndRecord("organizer@hua.gr", "guest@example.com");

        String result = rateLimiter.checkAndRecord("organizer@hua.gr", "guest@example.com");

        assertThat(result).isNotNull();
        assertThat(result).containsIgnoringCase("already");
    }

    @Test
    void emailCooldownIsCaseInsensitiveAndTrimmed() {
        rateLimiter.checkAndRecord("organizer@hua.gr", "  Guest@Example.com  ");

        String result = rateLimiter.checkAndRecord("organizer@hua.gr", "guest@example.com");

        assertThat(result).isNotNull();
    }

    @Test
    void differentTargetEmailsDoNotInterfereWithEachOther() {
        rateLimiter.checkAndRecord("organizer@hua.gr", "guest1@example.com");

        String result = rateLimiter.checkAndRecord("organizer@hua.gr", "guest2@example.com");

        assertThat(result).isNull();
    }

    @Test
    void cooldownAppliesGloballyPerEmailRegardlessOfOrganizer() {
        rateLimiter.checkAndRecord("organizerA@hua.gr", "guest@example.com");

        String result = rateLimiter.checkAndRecord("organizerB@hua.gr", "guest@example.com");

        assertThat(result).isNotNull();
    }

    @Test
    void organizerCanInviteUpToDailyCapDifferentEmails() {
        for (int i = 0; i < 20; i++) {
            String result = rateLimiter.checkAndRecord("organizer@hua.gr", "guest" + i + "@example.com");
            assertThat(result).as("invite #" + i + " should be allowed").isNull();
        }
    }

    @Test
    void organizerIsBlockedAfterExceedingDailyCap() {
        for (int i = 0; i < 20; i++) {
            rateLimiter.checkAndRecord("organizer@hua.gr", "guest" + i + "@example.com");
        }

        String result = rateLimiter.checkAndRecord("organizer@hua.gr", "guest20@example.com");

        assertThat(result).isNotNull();
        assertThat(result).containsIgnoringCase("today");
    }

    @Test
    void dailyCapIsTrackedSeparatelyPerOrganizer() {
        for (int i = 0; i < 20; i++) {
            rateLimiter.checkAndRecord("organizerA@hua.gr", "guestA" + i + "@example.com");
        }

        String result = rateLimiter.checkAndRecord("organizerB@hua.gr", "guestB@example.com");

        assertThat(result).isNull();
    }
}