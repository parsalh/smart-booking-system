package com.hua.smartbooking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TitleRequest {
    @NotBlank(message = "Title cannot be blank")
    private String title;
}