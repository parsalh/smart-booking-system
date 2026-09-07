package com.hua.smartbooking.dto;

/**
 * A user returned from GET /api/users/search or
 * /api/users/frequent-collaborators, with just enough info to show
 * and select them as a meeting participant.
 *
 * @author Stavroula Parsali
 */
public record UserSearchResult(
        Long id,
        String fullname,
        String email,
        String avatarUrl,
        String outOfOfficeStart,
        String outOfOfficeEnd
) {
}