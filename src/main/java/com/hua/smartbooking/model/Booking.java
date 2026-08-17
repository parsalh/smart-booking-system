package com.hua.smartbooking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_time", columnList = "start_time, end_time")
        }
    )
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = true)
    @JoinColumn(name = "room_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Room room;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "google_event_id")
    private String googleEventId;

    @ElementCollection
    @CollectionTable(name = "booking_participants", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "participant_email")
    private List<String> participants = new ArrayList<>();

    public enum BookingStatus {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

}
