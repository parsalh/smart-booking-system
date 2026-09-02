package com.hua.smartbooking.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import com.hua.smartbooking.exception.StaleGoogleTokenException;
import com.hua.smartbooking.exception.UserNotRegisteredException;
import com.hua.smartbooking.factory.GoogleCalendarClientFactory;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service dedicated to interfacing with the Google Free/Busy API.
 * @author Stavroula Parsali
 */
@Service
public class AvailabilityService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final UserRepository userRepository;
    private final GoogleCalendarClientFactory calendarClientFactory;

    public AvailabilityService(UserRepository userRepository,
                               GoogleCalendarClientFactory calendarClientFactory) {
        this.userRepository = userRepository;
        this.calendarClientFactory = calendarClientFactory;
    }

    public Map<String, List<TimePeriod>> fetchGroupAvailability(List<String> participantEmails,
                                                                ZonedDateTime searchStart,
                                                                ZonedDateTime searchEnd,
                                                                User organizer) throws GeneralSecurityException, IOException {

        Map<String, List<TimePeriod>> userBusyBlocks = new HashMap<>();

        for (String email : participantEmails) {
            User participant = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotRegisteredException("User Not Found: " + email, email));

            if (participant.getRefreshToken() == null) {
                throw new UserNotRegisteredException(
                        "User is registered but has no Google Calendar access token: " + email, email);
            }

            Calendar service = calendarClientFactory.buildClient(participant.getRefreshToken());

            FreeBusyRequest request = new FreeBusyRequest();
            request.setTimeMin(new DateTime(searchStart.toInstant().toEpochMilli()));
            request.setTimeMax(new DateTime(searchEnd.toInstant().toEpochMilli()));
            request.setItems(List.of(new FreeBusyRequestItem().setId("primary")));

            FreeBusyResponse response;
            try {
                response = service.freebusy().query(request).execute();
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("invalid_grant")) {
                    participant.setRefreshToken(null);
                    userRepository.save(participant);
                    throw new StaleGoogleTokenException(
                            "Google Calendar access for " + email + " has expired. They need to sign in again.",
                            email);
                }
                throw e;
            }

            FreeBusyCalendar calendar = response.getCalendars().get("primary");
            boolean hasError = calendar != null && calendar.getErrors() != null && !calendar.getErrors().isEmpty();

            List<TimePeriod> busyBlocks;
            if (calendar == null || hasError) {
                busyBlocks = new ArrayList<>();
            } else {
                List<TimePeriod> busy = calendar.getBusy();
                busyBlocks = busy != null ? new ArrayList<>(busy) : new ArrayList<>();
            }

            addOutOfOfficeBlock(participant, searchStart, searchEnd, busyBlocks);

            userBusyBlocks.put(email, busyBlocks);
        }

        return userBusyBlocks;
    }

    private void addOutOfOfficeBlock(User participant, ZonedDateTime searchStart, ZonedDateTime searchEnd,
                                     List<TimePeriod> busyBlocks) {
        if (participant.getOutOfOfficeStart() == null || participant.getOutOfOfficeEnd() == null) {
            return;
        }

        long oooStartMillis = participant.getOutOfOfficeStart().toEpochMilli();
        long oooEndMillis = participant.getOutOfOfficeEnd().toEpochMilli();

        boolean overlaps = oooStartMillis < searchEnd.toInstant().toEpochMilli()
                && oooEndMillis > searchStart.toInstant().toEpochMilli();

        if (overlaps) {
            busyBlocks.add(new TimePeriod()
                    .setStart(new DateTime(oooStartMillis))
                    .setEnd(new DateTime(oooEndMillis)));
        }
    }

}