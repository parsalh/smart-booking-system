package com.hua.smartbooking.dto;

import com.hua.smartbooking.enums.RsvpStatus;
import lombok.Data;

@Data
public class RsvpRequest {
    private RsvpStatus status;
}