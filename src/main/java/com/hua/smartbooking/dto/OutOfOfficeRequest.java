package com.hua.smartbooking.dto;

import lombok.Data;

/**
 * Request body for POST /api/profile/out-of-office: the start and
 * end date of the user's Out of Office period. Both fields are
 * intentionally unvalidated (no @NotBlank): sending them blank or
 * null is how the user clears their Out-of-Office status.
 *
 * @author Stavroula Parsali
 */
@Data
public class OutOfOfficeRequest {
    private String startDate;
    private String endDate;
}