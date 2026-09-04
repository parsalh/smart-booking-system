package com.hua.smartbooking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the current session's CSRF token as JSON, purely so it can be fetched
 * and pasted into Swagger UI's "Authorize" dialog when testing POST/PUT/DELETE
 * endpoints. Swagger UI has no automatic way to read the _csrf meta tag that
 * SmartBooking's own Thymeleaf pages use.
 *
 * @author Stavroula Parsali
 */
@RestController
@Tag(name = "CSRF", description = "Fetch a CSRF token for testing state-changing endpoints from Swagger UI")
public class CsrfTokenController {

    @Operation(
            summary = "Get a CSRF token for the current session",
            description = "Call this first, then paste the returned token into Swagger UI's Authorize dialog "
                    + "(csrfToken scheme) to test POST/PUT/DELETE endpoints. Requires being logged into SmartBooking."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current CSRF token and the header name it must be sent under")
    })
    @SecurityRequirements
    @GetMapping("/api/csrf-token")
    public Map<String, String> getCsrfToken(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "token", token.getToken()
        );
    }
}