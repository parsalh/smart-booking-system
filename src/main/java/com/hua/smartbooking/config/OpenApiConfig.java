package com.hua.smartbooking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the OpenAPI / Swagger UI documentation.
 * Swagger UI is available at /swagger-ui.html once the application is running.
 *
 * Note: since authentication is session-based (Google OAuth2 login), "Try it out"
 * in Swagger UI only works if you're logged into SmartBooking in the same browser
 * session, there is no separate API key/token to paste in for authentication.
 *
 * @author Stavroula Parsali
 */
@Configuration
public class OpenApiConfig {

    private static final String CSRF_SCHEME_NAME = "csrfToken";

    @Bean
    public OpenAPI smartBookingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartBooking API")
                        .description("REST API for SmartBooking — a smart meeting room booking system. "
                                + "Covers booking suggestions, room availability, invitations, RSVPs, "
                                + "user profile preferences, and admin user/role management.\n\n"
                                + "**Testing POST/PUT/DELETE endpoints here:** call `GET /api/csrf-token` first, "
                                + "then click **Authorize** (top right) and paste the returned token under the "
                                + "`csrfToken` scheme. You must also be logged into SmartBooking in this same "
                                + "browser session.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Stavroula Parsali")))
                .components(new Components()
                        .addSecuritySchemes(CSRF_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-CSRF-TOKEN")
                                .description("Get this from GET /api/csrf-token. Required for any POST/PUT/DELETE request.")))
                .addSecurityItem(new SecurityRequirement().addList(CSRF_SCHEME_NAME));
    }
}