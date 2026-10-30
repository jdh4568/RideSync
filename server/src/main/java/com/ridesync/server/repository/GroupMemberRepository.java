package com.ridesync.server.repository;
import com.ridesync.server.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);
}
