package com.ridesync.server;

import com.ridesync.server.dto.CreateGroupRequest;
import com.ridesync.server.repository.GroupMemberRepository;
import com.ridesync.server.service.GroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 실제 HTTP/MySQL 시나리오. 롤백 테스트에서만 저장 실패를 주입한다. */
@EnabledIfEnvironmentVariable(named = "RIDEMATE_LIVE_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.show-sql=false", "server.address=127.0.0.1"})
class GroupLiveIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService service;
    @MockitoSpyBean GroupMemberRepository members;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<String> accounts = new ArrayList<>();

    private record Account(long id, String token) {}

    private Account account() throws Exception {
        String name = "group_" + UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        accounts.add(name);
        JsonNode signup = call("POST", "/api/v1/auth/signup", null,
                Map.of("loginId", name, "nickname", name, "password", password, "passwordConfirm", password), 201);
        JsonNode login = call("POST", "/api/v1/auth/login", null,
                Map.of("loginId", name, "password", password), 200);
        return new Account(signup.get("userId").asLong(), login.get("accessToken").asText());
    }

    private JsonNode call(String method, String path, Account account, Object payload, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (account != null) request.header("Authorization", "Bearer " + account.token());
        var body = payload == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload));
        if (payload != null) request.header("Content-Type", "application/json");
        var response = client.send(request.method(method, body).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), method + " " + path);
        return json.readTree(response.body());
    }

    private long create(Account owner) throws Exception {
        JsonNode result = call("POST", "/api/v1/groups", owner,
                Map.of("groupName", "통합 검증", "description", "검증용", "maxMembers", 2), 201);
        assertEquals(2, result.size());
        return result.get("groupId").asLong();
    }

    private JsonNode groupRow(Account account, long groupId) throws Exception {
        for (JsonNode row : call("GET", "/api/v1/groups", account, null, 200)) {
            if (row.get("groupId").asLong() == groupId) return row;
        }
        throw new AssertionError("그룹 목록에 생성한 그룹이 없습니다.");
    }

    private void cleanup() {
        for (String name : accounts) {
            var ids = jdbc.queryForList("select user_id from user where login_id = ?", Long.class, name);
            for (Long id : ids) {
                jdbc.update("delete m from group_member m join riding_group g on m.group_id = g.group_id where g.owner_id = ?", id);
                jdbc.update("delete from riding_group where owner_id = ?", id);
                jdbc.update("delete from group_member where user_id = ?", id);
                jdbc.update("delete from user where user_id = ?", id);
            }
            assertEquals(0, jdbc.queryForObject("select count(*) from user where login_id = ?", Integer.class, name));
        }
    }

    @Test
    void authenticationThroughGroupApprovalAndRejectionOverHttp() throws Exception {
        try {
            Account owner = account();
            Account applicant = account();
            Account other = account();
            long first = create(owner);
            long second = create(owner);
            long foreign = create(other);
            String root = "/api/v1/groups/" + first;
            String decision = root + "/requests/" + applicant.id();
            assertEquals(1, groupRow(owner, first).get("memberCount").asInt());
            assertEquals("OWNER", groupRow(owner, first).get("myRole").asText());
            assertTrue(groupRow(applicant, first).get("membershipStatus").isNull());
            assertEquals(0, call("GET", "/api/v1/groups/my", applicant, null, 200).size());
            call("GET", "/api/v1/groups", null, null, 401);
            call("GET", "/api/v1/groups/join-requests", applicant, null, 403);
            assertEquals("PENDING", call("POST", root + "/join", applicant, null, 200).get("status").asText());
            call("POST", root + "/join", applicant, null, 409);
            call("POST", "/api/v1/groups/" + second + "/join", applicant, null, 200);
            call("POST", "/api/v1/groups/" + foreign + "/join", applicant, null, 200);
            JsonNode requests = call("GET", "/api/v1/groups/join-requests", owner, null, 200);
            assertEquals(2, requests.size());
            assertEquals(6, requests.get(0).size());
            assertEquals("PENDING", groupRow(applicant, first).get("membershipStatus").asText());
            assertEquals(0, call("GET", "/api/v1/groups/my", applicant, null, 200).size());
            call("POST", decision + "/approve", other, null, 403);
            long memberId = jdbc.queryForObject("select group_member_id from group_member where group_id=? and user_id=?",
                    Long.class, first, applicant.id());
            call("POST", decision + "/reject", owner, null, 200);
            assertEquals("REJECTED", groupRow(applicant, first).get("membershipStatus").asText());
            call("POST", decision + "/approve", owner, null, 409);
            call("POST", root + "/join", applicant, null, 200);
            assertEquals(memberId, jdbc.queryForObject("select group_member_id from group_member where group_id=? and user_id=?",
                    Long.class, first, applicant.id()));
            JsonNode approved = call("POST", decision + "/approve", owner, null, 200);
            assertEquals(3, approved.size());
            assertEquals(applicant.id(), approved.get("userId").asLong());
            assertEquals("APPROVED", approved.get("status").asText());
            assertNotNull(jdbc.queryForObject("select joined_at from group_member where group_member_id=?",
                    java.sql.Timestamp.class, memberId));
            assertEquals(2, groupRow(applicant, first).get("memberCount").asInt());
            JsonNode mine = call("GET", "/api/v1/groups/my", applicant, null, 200);
            assertEquals(1, mine.size());
            assertEquals(first, mine.get(0).get("groupId").asLong());
            assertEquals("MEMBER", mine.get(0).get("role").asText());
            call("POST", decision + "/reject", owner, null, 409);
            call("POST", root + "/join", applicant, null, 409);
            call("POST", root + "/join", other, null, 200);
            assertEquals("GROUP_FULL", call("POST", root + "/requests/" + other.id() + "/approve",
                    owner, null, 409).get("code").asText());
            assertEquals("PENDING", groupRow(other, first).get("membershipStatus").asText());
            call("POST", root + "/requests/" + other.id() + "/reject", owner, null, 200);
            call("POST", "/api/v1/groups/" + second + "/requests/" + applicant.id() + "/reject", owner, null, 200);
            assertEquals(0, call("GET", "/api/v1/groups/join-requests", owner, null, 200).size());
            System.out.println("LIVE PASS: seven group APIs, JWT, rejection/reapply, approval, capacity and owner isolation");
        } finally {
            cleanup();
        }
    }

    @Test
    void memberSaveFailureRollsBackGroupInsert() throws Exception {
        try {
            Account owner = account();
            CreateGroupRequest request = new CreateGroupRequest();
            request.setGroupName("rollback-check");
            request.setMaxMembers(2);
            doThrow(new IllegalStateException("injected member save failure")).when(members).save(any());
            assertThrows(IllegalStateException.class, () -> service.createGroup(owner.id(), request));
            assertEquals(0, jdbc.queryForObject("select count(*) from riding_group where owner_id=?",
                    Integer.class, owner.id()));
            assertEquals(0, jdbc.queryForObject("select count(*) from group_member where user_id=?",
                    Integer.class, owner.id()));
            System.out.println("LIVE PASS: injected member save failure rolls back actual group INSERT");
        } finally {
            reset(members);
            cleanup();
        }
    }
}
