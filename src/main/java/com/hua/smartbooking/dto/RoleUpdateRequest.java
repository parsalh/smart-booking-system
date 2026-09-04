package com.hua.smartbooking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleUpdateRequest {

    @NotBlank(message = "Role cannot be blank")
    private String role;
}