package com.ridesync.server.repository;
import com.ridesync.server.entity.RidingGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ridesync.server.dto.GroupListResponse;
import com.ridesync.server.dto.MyGroupResponse;
import java.util.List;

public interface RidingGroupRepository extends JpaRepository<RidingGroup, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from RidingGroup g where g.id = :groupId")
    java.util.Optional<RidingGroup> findByIdForUpdate(@Param("groupId") Long groupId);

    @Query("""
            select new com.ridesync.server.dto.GroupListResponse(
                g.id, g.groupName, g.description, count(approved.id),
                g.maxMembers, mine.status, mine.role)
            from RidingGroup g
            left join GroupMember approved on approved.group = g and approved.status = 'APPROVED'
            left join GroupMember mine on mine.group = g and mine.user.id = :userId
            group by g.id, g.groupName, g.description, g.maxMembers, mine.status, mine.role
            order by g.id
            """)
    List<GroupListResponse> findGroupList(@Param("userId") Long userId);

    @Query("""
            select new com.ridesync.server.dto.MyGroupResponse(
                g.id, g.groupName, mine.role, count(approved.id))
            from RidingGroup g
            join GroupMember mine on mine.group = g
                and mine.user.id = :userId and mine.status = 'APPROVED'
            left join GroupMember approved on approved.group = g and approved.status = 'APPROVED'
            group by g.id, g.groupName, mine.role
            order by g.id
            """)
    List<MyGroupResponse> findMyGroups(@Param("userId") Long userId);
}
