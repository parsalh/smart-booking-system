package com.hua.smartbooking.model;


import com.hua.smartbooking.util.StringCryptoConverter;
import jakarta.persistence.*;
import lombok.*;
import com.hua.smartbooking.enums.Role;

import java.time.Instant;

/**
 * A registered SmartBooking user, authenticated via Google OAuth2.
 * Stores their role, Google refresh token (used to access their
 * Calendar), avatar, Out-of-Office period, and preferred title.
 * The full name and refresh token are encrypted at rest.
 *
 * @author Stavroula Parsali
 */
@Entity
@Table(name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_user_google_id", columnNames = "google_sub_id")
            }
        )
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Convert(converter = StringCryptoConverter.class)
    @Column(columnDefinition = "TEXT")
    private String fullname;

    @Column(name = "google_sub_id", unique = true)
    private String googleSubId;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Convert(converter = StringCryptoConverter.class)
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    private Instant outOfOfficeStart;
    private Instant outOfOfficeEnd;

    private String title;

}
