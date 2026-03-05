package com.hua.smartbooking.model;


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

    private String fullname;

    @Column(name = "google_sub_id", unique = true)
    private String googleSubId;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    public enum Role {
        ADMIN,
        STUDENT,
        PROFESSOR
    }
}
