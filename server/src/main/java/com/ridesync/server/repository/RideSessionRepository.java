package com.ridesync.server.repository;

import com.ridesync.server.entity.RideSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideSessionRepository extends JpaRepository<RideSession, Long> {}
