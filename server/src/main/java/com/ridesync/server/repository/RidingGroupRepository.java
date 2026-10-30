package com.ridesync.server.repository;
import com.ridesync.server.entity.RidingGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RidingGroupRepository extends JpaRepository<RidingGroup, Long> {
}
