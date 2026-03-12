package com.hua.smartbooking.repository;

import com.hua.smartbooking.model.Event;
import com.hua.smartbooking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByUser(User user);
    boolean existsByGoogleEventId(String googleEventId);
}
