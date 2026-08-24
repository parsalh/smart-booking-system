package com.hua.smartbooking.repository;

import com.hua.smartbooking.enums.BookingStatus;
import com.hua.smartbooking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b 
        WHERE b.room.id = :roomId 
        AND b.status != 'CANCELLED' 
        AND b.startTime < :endTime 
        AND b.endTime > :startTime
    """)
    boolean hasConflictingBookings(@Param("roomId") Long roomId,
                                   @Param("startTime") Instant startTime,
                                   @Param("endTime") Instant endTime);


    List<Booking> findByRoomId(Long roomId);

    Optional<Booking> findByGoogleEventId(String googleEventId);

    List<Booking> findByStartTimeAfterAndStatusNot(Instant now, BookingStatus excludedStatus);
}
