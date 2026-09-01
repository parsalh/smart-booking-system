package com.hua.smartbooking.dto;

import lombok.Data;

import java.util.List;

@Data
public class FinalBookingRequest {

    private Long roomId;
    private String title;
    private String startTime;
    private String endTime;
    private List<String> participants;

    private Integer repeatWeeks;
    private Boolean forcePartial;

}
