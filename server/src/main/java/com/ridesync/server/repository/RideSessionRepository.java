package com.ridesync.server.repository;

import com.ridesync.server.entity.RideSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideSessionRepository extends JpaRepository<RideSession, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from RideSession r where r.id = :id")
    java.util.Optional<RideSession> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
