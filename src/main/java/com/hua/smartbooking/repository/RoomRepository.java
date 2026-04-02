package com.hua.smartbooking.repository;

import com.hua.smartbooking.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByNameIgnoreCase(String name);

    @Query("""
        SELECT r FROM Room r 
        WHERE r.isAvailable = true 
        AND r.capacity >= :minCapacity 
        AND NOT EXISTS (
            SELECT b FROM Booking b 
            WHERE b.room = r 
            AND b.status != 'CANCELLED'
            AND b.startTime < :end AND b.endTime > :start
        )
        AND NOT EXISTS (
            SELECT e FROM Event e 
            WHERE e.room = r 
            AND e.startTime < :end AND e.endTime > :start
        )
    """)
    List<Room> findAvailableRooms(
            @Param("minCapacity") Integer minCapacity,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
