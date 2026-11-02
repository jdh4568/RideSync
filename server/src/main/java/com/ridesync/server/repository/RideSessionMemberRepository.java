package com.ridesync.server.repository;

import com.ridesync.server.entity.RideSessionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RideSessionMemberRepository extends JpaRepository<RideSessionMember, Long> {
    boolean existsByRideSession_IdAndUser_Id(Long rideSessionId, Long userId);

    long countByRideSession_Id(Long rideSessionId);

    Optional<RideSessionMember> findByRideSession_IdAndUser_Id(Long rideSessionId, Long userId);
}
