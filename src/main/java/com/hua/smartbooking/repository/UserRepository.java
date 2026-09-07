package com.hua.smartbooking.repository;

import com.hua.smartbooking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;

/**
 * Spring Data repository for User entities: lookup by email, and
 * searching users by name or email for the participant picker.
 *
 * @author Stavroula Parsali
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    List<User> findByFullnameContainingIgnoreCaseOrEmailContainingIgnoreCase(String fullname, String email);
}
