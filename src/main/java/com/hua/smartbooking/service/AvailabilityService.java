package com.hua.smartbooking.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
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

            FreeBusyResponse response = service.freebusy().query(request).execute();

            FreeBusyCalendar calendar = response.getCalendars().get("primary");
            boolean hasError = calendar != null && calendar.getErrors() != null && !calendar.getErrors().isEmpty();

            if (calendar == null || hasError) {
                userBusyBlocks.put(email, new ArrayList<>());
            } else {
                List<TimePeriod> busy = calendar.getBusy();
                userBusyBlocks.put(email, busy != null ? busy : new ArrayList<>());
            }
        }

        return userBusyBlocks;
    }

}
