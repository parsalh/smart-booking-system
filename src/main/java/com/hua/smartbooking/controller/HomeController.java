package com.hua.smartbooking.controller;

import com.google.api.services.calendar.model.Event;
import com.hua.smartbooking.model.Room;
import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.RoomRepository;
import com.hua.smartbooking.repository.UserRepository;
import com.hua.smartbooking.service.GoogleCalendarService;
import com.hua.smartbooking.service.EventMappingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final RoomRepository roomRepository;

    public HomeController(OAuth2AuthorizedClientService authorizedClientService,
                          UserRepository userRepository,
                          GoogleCalendarService googleCalendarService,
                          EventMappingService eventMappingService,
                          RoomRepository roomRepository) {
        this.authorizedClientService = authorizedClientService;
        this.userRepository = userRepository;
        this.googleCalendarService = googleCalendarService;
        this.eventMappingService = eventMappingService;
        this.roomRepository = roomRepository;
    }

    @GetMapping("/")
    public String home(OAuth2AuthenticationToken token, Model model) {
        if (token == null) {
            return "login";
        }

        String email = token.getPrincipal().getAttribute("email");

        User dbUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        model.addAttribute("name", dbUser.getFullname());
        model.addAttribute("email", dbUser.getEmail());
        model.addAttribute("avatar", dbUser.getAvatarUrl());
        model.addAttribute("role", dbUser.getRole().name());
        model.addAttribute("title", dbUser.getTitle());

        model.addAttribute("outOfOfficeStart",
                dbUser.getOutOfOfficeStart() != null
                        ? dbUser.getOutOfOfficeStart().atZone(java.time.ZoneId.of("Europe/Athens")).toLocalDate().toString()
                        : null);
        model.addAttribute("outOfOfficeEnd",
                dbUser.getOutOfOfficeEnd() != null
                        ? dbUser.getOutOfOfficeEnd().atZone(java.time.ZoneId.of("Europe/Athens")).toLocalDate().toString()
                        : null);

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

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/book")
    public String showBookingWizard(OAuth2AuthenticationToken token, Model model) {
        if (token == null) {
            return "redirect:/login";
        }

        String email = token.getPrincipal().getAttribute("email");

        User dbUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found in database"));

        model.addAttribute("name", dbUser.getFullname());
        model.addAttribute("email", dbUser.getEmail());
        model.addAttribute("avatar", dbUser.getAvatarUrl());
        model.addAttribute("role", dbUser.getRole().name());

        return "book";
    }

    @GetMapping("/rooms")
    public String browseRooms(Model model, @AuthenticationPrincipal OAuth2User principal) {
        model.addAttribute("rooms", roomRepository.findAll());

        if (principal != null) {
            String email = principal.getAttribute("email");
            User user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                model.addAttribute("name", user.getFullname());
                model.addAttribute("avatar", user.getAvatarUrl());
                model.addAttribute("role", user.getRole().name());
                model.addAttribute("email", user.getEmail());
            }
        }

        return "rooms";
    }

}