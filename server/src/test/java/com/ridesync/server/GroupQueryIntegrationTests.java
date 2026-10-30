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
