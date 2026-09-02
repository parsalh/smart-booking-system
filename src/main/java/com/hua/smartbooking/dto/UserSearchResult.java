package com.hua.smartbooking.dto;

public record UserSearchResult(
        Long id,
        String fullname,
        String email,
        String avatarUrl,
        String outOfOfficeStart,
        String outOfOfficeEnd
) {
}