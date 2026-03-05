package com.hua.smartbooking.model;


import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "rooms", uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_name", columnNames = "name")
    },
    indexes = {
            @Index(name = "idx_room_available", columnList = "is_available"),
            @Index(name = "idx_room_location", columnList = "location")
        }
    )
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private Integer capacity;

    private String location;

    @ElementCollection
    @CollectionTable(name = "room_amenities", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "amenity")
    private List<String> amenities;

    private Boolean isAvailable = true;

}
