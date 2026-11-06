package com.ridesync.server;

import com.ridesync.server.entity.*;
import com.ridesync.server.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GroupQueryIntegrationTests {
    @Autowired UserRepository users;
    @Autowired RidingGroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired com.ridesync.server.service.GroupService service;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void memberListIncludesSelfAndApprovedOnlyAndDeniesOtherStates() {
        User owner = user();
        User approved = user();
        User pending = user();
        User rejected = user();
        User outsider = user();
        RidingGroup g = group(owner);
        member(g, approved, "MEMBER", "APPROVED");
        member(g, pending, "MEMBER", "PENDING");
        member(g, rejected, "MEMBER", "REJECTED");
        members.flush();
        var response = service.getMembers(approved.getId(), g.getId());
        assertEquals(g.getId(), response.getGroupId());
        assertEquals(g.getGroupName(), response.getGroupName());
        assertEquals(g.getDescription(), response.getDescription());
        assertEquals(2L, response.getMemberCount());
        var result = response.getMembers();
        assertEquals(java.util.List.of(owner.getId(), approved.getId()),
                result.stream().map(com.ridesync.server.dto.GroupMemberResponse::getUserId).toList());
        assertEquals("OWNER", result.get(0).getRole());
        assertEquals(approved.getNickname(), result.get(1).getNickname());
        assertTrue(result.stream().noneMatch(com.ridesync.server.dto.GroupMemberResponse::isOnline));
        assertEquals(2, service.getMembers(owner.getId(), g.getId()).getMembers().size());
        for (User denied : java.util.List.of(pending, rejected, outsider)) {
            var error = assertThrows(com.ridesync.server.exception.ApiException.class,
                    () -> service.getMembers(denied.getId(), g.getId()));
            assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, error.getStatus());
        }
    }

    @Test
    void pendingRequestsAreOwnerScopedAndDecisionsRemoveThem() {
        User owner = user();
        User applicant = user();
        RidingGroup first = group(owner);
        RidingGroup second = group(owner);
        RidingGroup unrelated = group(user());
        service.joinGroup(applicant.getId(), first.getId());
        service.joinGroup(applicant.getId(), second.getId());
        service.joinGroup(applicant.getId(), unrelated.getId());
        var requests = service.getJoinRequests(owner.getId());
        assertEquals(2, requests.size());
        assertEquals(applicant.getNickname(), requests.get(0).getNickname());
        assertEquals(first.getGroupName(), requests.get(0).getGroupName());
        assertNotNull(requests.get(0).getGroupMemberId());
        assertEquals("APPROVED", service.approveRequest(owner.getId(), first.getId(), applicant.getId()).getStatus());
        assertEquals("REJECTED", service.rejectRequest(owner.getId(), second.getId(), applicant.getId()).getStatus());
        members.flush();
        assertTrue(service.getJoinRequests(owner.getId()).isEmpty());
        assertNotNull(members.findByGroup_IdAndUser_Id(first.getId(), applicant.getId()).orElseThrow().getJoinedAt());
        assertNull(members.findByGroup_IdAndUser_Id(second.getId(), applicant.getId()).orElseThrow().getJoinedAt());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentApprovalsCannotExceedCapacity() throws Exception {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        Long[] ids = tx.execute(s -> {
            User owner = user();
            User a = user();
            User b = user();
            RidingGroup g = group(owner);
            g.setMaxMembers(2);
            member(g, a, "MEMBER", "PENDING");
            member(g, b, "MEMBER", "PENDING");
            return new Long[]{g.getId(), owner.getId(), a.getId(), b.getId()};
        });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.function.Function<Long, String> approve = id -> {
                try {
                    start.await();
                    return service.approveRequest(ids[1], ids[0], id).getStatus();
                } catch (com.ridesync.server.exception.ApiException e) {
                    return e.getCode();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            };
            var a = pool.submit(() -> approve.apply(ids[2]));
            var b = pool.submit(() -> approve.apply(ids[3]));
            start.countDown();
            var results = java.util.List.of(a.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, java.util.Collections.frequency(results, "APPROVED"));
            assertEquals(1, java.util.Collections.frequency(results, "GROUP_FULL"));
            assertEquals(2L, jdbc.queryForObject(
                    "select count(*) from group_member where group_id = ? and status = 'APPROVED'",
                    Long.class, ids[0]));
            assertEquals(1L, jdbc.queryForObject(
                    "select count(*) from group_member where group_id = ? and status = 'PENDING'",
                    Long.class, ids[0]));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(s -> {
                jdbc.update("delete from group_member where group_id = ?", ids[0]);
                jdbc.update("delete from riding_group where group_id = ?", ids[0]);
                jdbc.update("delete from user where user_id in (?, ?, ?)", ids[1], ids[2], ids[3]);
            });
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentApplicationsCreateOnlyOneRow() throws Exception {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        Long[] ids = tx.execute(s -> {
            User owner = user();
            User applicant = user();
            return new Long[]{group(owner).getId(), owner.getId(), applicant.getId()};
        });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<String> attempt = () -> {
                start.await();
                try {
                    return service.joinGroup(ids[2], ids[0]).getStatus();
                } catch (com.ridesync.server.exception.ApiException e) {
                    return e.getCode();
                }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            start.countDown();
            var results = java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, java.util.Collections.frequency(results, "PENDING"));
            assertEquals(1, java.util.Collections.frequency(results, "GROUP_JOIN_CONFLICT"));
            assertEquals(1L, jdbc.queryForObject(
                    "select count(*) from group_member where group_id = ? and user_id = ?",
                    Long.class, ids[0], ids[2]));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(s -> {
                jdbc.update("delete from group_member where group_id = ?", ids[0]);
                jdbc.update("delete from riding_group where group_id = ?", ids[0]);
                jdbc.update("delete from user where user_id in (?, ?)", ids[1], ids[2]);
            });
        }
    }

    @Test
    void newApplicationAndRejectedReapplicationReuseRow() {
        RidingGroup g = group(user());
        User applicant = user();
        assertEquals("PENDING", service.joinGroup(applicant.getId(), g.getId()).getStatus());
        GroupMember first = members.findByGroup_IdAndUser_Id(g.getId(), applicant.getId()).orElseThrow();
        Long id = first.getId();
        assertEquals("MEMBER", first.getRole());
        assertNull(first.getJoinedAt());
        first.setStatus("REJECTED");
        members.flush();
        service.joinGroup(applicant.getId(), g.getId());
        members.flush();
        GroupMember second = members.findByGroup_IdAndUser_Id(g.getId(), applicant.getId()).orElseThrow();
        assertEquals(id, second.getId());
        assertEquals("PENDING", second.getStatus());
        assertNull(second.getJoinedAt());
    }

    private User user() {
        User u = new User();
        String name = UUID.randomUUID().toString();
        u.setLoginId(name);
        u.setNickname(name);
        u.setPassword("test-only");
        u.setCreatedAt(Instant.now());
        return users.save(u);
    }

    private RidingGroup group(User owner) {
        RidingGroup g = new RidingGroup();
        g.setOwner(owner);
        g.setGroupName("조회 검증");
        g.setDescription("설명");
        g.setMaxMembers(10);
        g.setCreatedAt(Instant.now());
        groups.save(g);
        member(g, owner, "OWNER", "APPROVED");
        return g;
    }

    private void member(RidingGroup g, User u, String role, String status) {
        GroupMember m = new GroupMember();
        m.setGroup(g);
        m.setUser(u);
        m.setRole(role);
        m.setStatus(status);
        if (status.equals("APPROVED")) m.setJoinedAt(Instant.now());
        members.save(m);
    }

    @Test
    void countsOnlyApprovedAndSeparatesCurrentUsersMembership() {
        User me = user();
        User other = user();
        RidingGroup owned = group(me);
        RidingGroup joined = group(other);
        RidingGroup pending = group(other);
        RidingGroup rejected = group(other);
        RidingGroup unrelated = group(other);
        member(owned, other, "MEMBER", "PENDING");
        member(joined, me, "MEMBER", "APPROVED");
        member(pending, me, "MEMBER", "PENDING");
        member(rejected, me, "MEMBER", "REJECTED");
        members.flush();

        var all = groups.findGroupList(me.getId());
        var mine = all.stream().filter(g -> g.getGroupId().equals(owned.getId())).findFirst().orElseThrow();
        assertEquals(1L, mine.getMemberCount());
        assertEquals("OWNER", mine.getMyRole());
        assertEquals("APPROVED", mine.getMembershipStatus());
        assertEquals("설명", mine.getDescription());
        assertEquals(10, mine.getMaxMembers());
        for (var g : all) {
            if (g.getGroupId().equals(pending.getId())) assertEquals("PENDING", g.getMembershipStatus());
            if (g.getGroupId().equals(rejected.getId())) assertEquals("REJECTED", g.getMembershipStatus());
            if (g.getGroupId().equals(unrelated.getId())) {
                assertNull(g.getMembershipStatus());
                assertNull(g.getMyRole());
            }
        }
        var my = groups.findMyGroups(me.getId());
        assertEquals(2, my.size());
        assertEquals("OWNER", my.get(0).getRole());
        assertEquals(joined.getId(), my.get(1).getGroupId());
        assertEquals("MEMBER", my.get(1).getRole());
        assertEquals(2L, my.get(1).getMemberCount());
        assertTrue(groups.findMyGroups(user().getId()).isEmpty());
    }
}
