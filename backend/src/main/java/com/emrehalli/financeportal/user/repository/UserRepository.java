package com.emrehalli.financeportal.user.repository;

import com.emrehalli.financeportal.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByKeycloakId(String keycloakId);

    @Query("""
            select u from User u
            where lower(u.fullName) like lower(concat('%', :search, '%'))
               or lower(u.email) like lower(concat('%', :search, '%'))
               or lower(coalesce(u.keycloakId, '')) like lower(concat('%', :search, '%'))
            """)
    Page<User> search(String search, Pageable pageable);

}



