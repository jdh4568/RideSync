package com.ridesync.server.repository;

import com.ridesync.server.entity.RideSessionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RideSessionMemberRepository extends JpaRepository<RideSessionMember, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from RideSessionMember m where m.rideSession.id = :rideId and m.user.id = :userId")
    Optional<RideSessionMember> findForRecordUpdate(
            @org.springframework.data.repository.query.Param("rideId") Long rideId,
            @org.springframework.data.repository.query.Param("userId") Long userId);

    boolean existsByRideSession_IdAndUser_Id(Long rideSessionId, Long userId);

    long countByRideSession_Id(Long rideSessionId);

    Optional<RideSessionMember> findByRideSession_IdAndUser_Id(Long rideSessionId, Long userId);
}
