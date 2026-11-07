package com.ridesync.server.repository;
import com.ridesync.server.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import com.ridesync.server.dto.GroupJoinRequestResponse;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    boolean existsByGroup_IdAndUser_IdAndStatus(Long groupId, Long userId, String status);

    @Query("""
            select m from GroupMember m join fetch m.user
            where m.group.id = :groupId and m.status = 'APPROVED'
            order by m.id
            """)
    List<GroupMember> findApprovedMembers(@Param("groupId") Long groupId);

    @Query("""
            select new com.ridesync.server.dto.GroupJoinRequestResponse(
                m.id, g.id, g.groupName, u.id, u.nickname, m.status)
            from GroupMember m join m.group g join m.user u
            where g.owner.id = :ownerId and m.status = 'PENDING'
            order by m.id
            """)
    List<GroupJoinRequestResponse> findPendingRequests(@Param("ownerId") Long ownerId);

    // 잠금 조회로 동시 승인 후 커밋된 최신 인원을 확인한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<GroupMember> findByGroup_IdAndStatus(Long groupId, String status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);
}
