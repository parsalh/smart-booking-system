package com.hua.smartbooking.security;

import com.hua.smartbooking.model.User;
import com.hua.smartbooking.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public LoginSuccessHandler(UserRepository userRepository, OAuth2AuthorizedClientService authorizedClientService) {
        this.userRepository = userRepository;
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();

        if (oauth2User != null) {
            String email = oauth2User.getAttribute("email");

            if (email != null) {
                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken.getName()
                );

                if (client != null && client.getRefreshToken() != null) {
                    String newRefreshToken = client.getRefreshToken().getTokenValue();

                    userRepository.findByEmail(email).ifPresent(user -> {
                        user.setRefreshToken(newRefreshToken);
                        userRepository.save(user);
                        System.out.println("Successfully updated refresh token in DB for user: " + email);
                    });
                }
            }
        }

        response.sendRedirect("/");
    }
}