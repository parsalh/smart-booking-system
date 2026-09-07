package com.hua.smartbooking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/profile/title: the user's preferred
 * title (Mr./Mrs./Ms./Dr.).
 *
 * @author Stavroula Parsali
 */
@Data
public class TitleRequest {
    @NotBlank(message = "Title cannot be blank")
    private String title;
}