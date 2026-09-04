package com.hua.smartbooking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OutOfOfficeRequest {

    @NotBlank(message = "Start date is required")
    private String startDate;

    @NotBlank(message = "End date is required")
    private String endDate;
}