package com.hua.smartbooking.model;

import com.hua.smartbooking.util.StringCryptoConverter;
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
@Table(name = "events")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = StringCryptoConverter.class)
    @Column(length = 500)
    private String title;

    @Convert(converter = StringCryptoConverter.class)
    @Column(length = 1000)
    private String description;

    private Instant startTime;
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private String googleEventId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "room_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Room room;

    @Convert(converter = StringCryptoConverter.class)
    @ElementCollection
    @CollectionTable(name = "event_participants", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "participant_email", length = 500)
    private List<String> participants = new ArrayList<>();

    public enum EventType {
        LECTURE,
        LAB,
        MEETING,
        OFFICE_HOURS,
        OTHER
    }

}
