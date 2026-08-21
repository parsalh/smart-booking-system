package com.hua.smartbooking.model;


import com.hua.smartbooking.util.StringCryptoConverter;
import jakarta.persistence.*;
import lombok.*;

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
    @Column(length = 500)
    private String fullname;

    @Column(name = "google_sub_id", unique = true)
    private String googleSubId;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Convert(converter = StringCryptoConverter.class)
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "avatar_url")
    private String avatarUrl;

    public enum Role {
        ADMIN,
        STUDENT,
        PROFESSOR
    }
}
