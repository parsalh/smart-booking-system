package com.hua.smartbooking.dto;

import lombok.Data;

@Data
public class OutOfOfficeRequest {
    private String startDate;
    private String endDate;
}