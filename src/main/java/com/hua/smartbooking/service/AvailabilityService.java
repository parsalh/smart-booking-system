package com.hua.smartbooking.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.FreeBusyRequest;
import com.google.api.services.calendar.model.FreeBusyRequestItem;
import com.google.api.services.calendar.model.FreeBusyResponse;
import com.google.api.services.calendar.model.TimePeriod;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import com.hua.smartbooking.exception.UserNotRegisteredException;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service dedicated to interfacing with the Google Free/Busy API.
 */
@Service
public class AvailabilityService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final UserRepository userRepository;

    public AvailabilityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<String, List<TimePeriod>> fetchGroupAvailability(List<String> participantEmails,
                                                                ZonedDateTime searchStart,
                                                                ZonedDateTime searchEnd,
                                                                User organizer) throws Exception {

        for (String email : participantEmails) {
            User participant = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotRegisteredException(
                            "User Not Found: " + email, email
                    ));

            if (participant.getRefreshToken() == null) {
                throw new UserNotRegisteredException(
                        "User is registered but has no Google Calendar access token: " + email,email
                );
            }
        }

        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(organizer.getRefreshToken())
                .build();

        Calendar service = new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("SmartBooking")
                .build();

        List<FreeBusyRequestItem> items = new ArrayList<>();
        for (String email : participantEmails) {
            items.add(new FreeBusyRequestItem().setId(email));
        }

        FreeBusyRequest request = new FreeBusyRequest();
        request.setTimeMin(new DateTime(searchStart.toInstant().toEpochMilli()));
        request.setTimeMax(new DateTime(searchEnd.toInstant().toEpochMilli()));
        request.setItems(items);

        FreeBusyResponse response = service.freebusy().query(request).execute();

        Map<String, List<TimePeriod>> userBusyBlocks = new HashMap<>();
        for (String email : participantEmails) {
            if (response.getCalendars().containsKey(email)) {
                List<TimePeriod> busyTimes = response.getCalendars().get(email).getBusy();
                userBusyBlocks.put(email, busyTimes != null ? busyTimes : new ArrayList<>());
            } else {
                userBusyBlocks.put(email, new ArrayList<>());
            }
        }

        return userBusyBlocks;

    }

}
