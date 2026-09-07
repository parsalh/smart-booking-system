package com.hua.smartbooking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /admin/users/{id}/role: the new role to
 * assign to a user, as a string matched against the {@link
 * com.hua.smartbooking.enums.Role} enum values.
 *
 * @author Stavroula Parsali
 */
@Data
public class RoleUpdateRequest {

    @NotBlank(message = "Role cannot be blank")
    private String role;
}