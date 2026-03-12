package com.hua.smartbooking.controller;

import com.google.api.services.calendar.model.Event;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.UserRepository;
import com.hua.smartbooking.service.GoogleCalendarService;
import com.hua.smartbooking.service.EventMappingService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;


@Controller
public class HomeController {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository;
    private final GoogleCalendarService googleCalendarService;
    private final EventMappingService eventMappingService;

    public HomeController(OAuth2AuthorizedClientService authorizedClientService,
                          UserRepository userRepository,
                          GoogleCalendarService googleCalendarService,
                          EventMappingService eventMappingService) {
        this.authorizedClientService = authorizedClientService;
        this.userRepository = userRepository;
        this.googleCalendarService = googleCalendarService;
        this.eventMappingService = eventMappingService;
    }

    @GetMapping("/")
    public String home(OAuth2AuthenticationToken token, Model model) {
        if (token == null) {
            return "login";
        }

        OAuth2User oAuth2User = token.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        model.addAttribute("name", oAuth2User.getAttribute("name"));
        model.addAttribute("email", email);

        User dbUser = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        String currentRefreshToken = handleRefreshToken(token, dbUser);

        if (currentRefreshToken != null) {
            try {
                List<Event> googleEvents = googleCalendarService.getUpcomingEvents(currentRefreshToken);

                eventMappingService.syncEvents(googleEvents, dbUser);

                String eventsJson = googleCalendarService.getEventsAsJsonForCalendar(currentRefreshToken, dbUser);
                model.addAttribute("eventsJson", eventsJson);

                long meetingCount = eventMappingService.countMeetingsForUser(dbUser);
                model.addAttribute("eventCount", meetingCount);

            } catch (Exception e) {
                model.addAttribute("error", "Unable to sync calendar");
                model.addAttribute("eventsJson", "[]");
            }
        } else {
            model.addAttribute("eventsJson", "[]");
        }

        return "index";
    }

    /**
     * Helper μέθοδος για τη διαχείριση του token (για να μείνει καθαρός ο home)
     */
    private String handleRefreshToken(OAuth2AuthenticationToken token, User dbUser) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName());

        if (client != null && client.getRefreshToken() != null) {
            String tokenValue = client.getRefreshToken().getTokenValue();
            if (dbUser.getRefreshToken() == null || !dbUser.getRefreshToken().equals(tokenValue)) {
                dbUser.setRefreshToken(tokenValue);
                userRepository.save(dbUser);
            }
            return tokenValue;
        }
        return dbUser.getRefreshToken();
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}