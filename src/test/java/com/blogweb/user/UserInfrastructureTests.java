package com.blogweb.user;

import com.blogweb.user.infrastructure.*;
import com.blogweb.user.dto.EmailCodePurpose;
import com.blogweb.user.mapper.UserMapper;
import com.blogweb.user.entity.User;
import com.blogweb.common.util.EmailUtil;
import com.blogweb.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "blogweb.email-code.enabled=true", "spring.mail.username=sender@qq.com", "spring.mail.password=test-only",
        "management.health.mail.enabled=false"
})
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "BLOGWEB_INTEGRATION_TEST", matches = "true")
class UserInfrastructureTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired StringRedisTemplate redis;
    @Autowired EmailCodeStore codes;
    @Autowired LoginRateLimiter limiter;
    @MockitoBean EmailUtil mail;
    private final String email = "blogwebtest" + UUID.randomUUID().toString().replace("-", "") + "@qq.com";
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private String csrf;

    @AfterEach
    void cleanup() {
        users.delete(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        String prefix = "blogweb:auth:email:{" + email + "}:";
        redis.delete(List.of(prefix + "code", prefix + "cooldown", prefix + "attempts", prefix + "login:code", prefix + "login:attempts", prefix + "reservation", prefix + "login:reservation", "blogweb:auth:login:" + email));
    }

    @Test
    void registerLoginAndDeleteSessionThroughRealHttp() throws Exception {
        refreshCsrf();
        assertThat(request("POST", "/email-verification-codes", Map.of("email", email)).statusCode()).isEqualTo(202);
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail, timeout(5000)).sendCode(eq(email), code.capture());
        var registration = Map.of("email", email, "password", "TestPassword123!", "nickname", "Integration", "code", code.getValue());
        var registered = request("POST", "/users/register", registration);
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(json.readTree(registered.body()).at("/data/role").asText()).isEqualTo("USER");
        String hash = users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email)).getPassword();
        assertThat(hash).isEqualTo("TestPassword123!");
        assertThat(codes.consume(email, code.getValue())).isFalse();
        assertThat(request("POST", "/users/register", registration).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/sessions", Map.of("email", email, "password", "wrong")).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/sessions/loginbypassword", Map.of("email", email, "password", "TestPassword123!")).statusCode()).isEqualTo(201);
        var me = request("GET", "/users/me", Map.of());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body()).at("/data/email").asText()).isEqualTo(email);
        assertThat(json.readTree(me.body()).at("/data/password").isMissingNode()).isTrue();
        assertThat(request("DELETE", "/sessions/current", Map.of()).statusCode()).isEqualTo(403);
        refreshCsrf();
        var logout = request("DELETE", "/sessions/current", Map.of());
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.body()).isEmpty();
        assertThat(request("GET", "/users/me", Map.of()).statusCode()).isEqualTo(401);
        refreshCsrf();
        redis.delete("blogweb:auth:email:{" + email + "}:cooldown"); // Simulate passing resend interval.
        assertThat(request("POST", "/email-verification-codes", Map.of("email", email, "purpose", "LOGIN"))
                .statusCode()).isEqualTo(202);
        var loginCode = ArgumentCaptor.forClass(String.class);
        verify(mail, timeout(5000).times(2)).sendCode(eq(email), loginCode.capture());
        var loginBody = Map.of("email", email, "code", loginCode.getAllValues().get(1));
        assertThat(request("POST", "/sessions/loginbycode", loginBody).statusCode()).isEqualTo(201);
        assertThat(request("GET", "/users/me", Map.of()).statusCode()).isEqualTo(200);
        refreshCsrf();
        assertThat(request("DELETE", "/sessions/current", Map.of()).statusCode()).isEqualTo(204);
        refreshCsrf();
        assertThat(request("POST", "/sessions/loginbycode", loginBody).statusCode()).isEqualTo(401);

    }

    @Test
    void redisCodeConsumptionAndLimitsRemainAtomic() throws Exception {
        assertThat(codes.reserve(email, "012345", "first")).isTrue();
        String prefix = "blogweb:auth:email:{" + email + "}:";
        assertThat(codes.isCurrent(email, "first", EmailCodePurpose.REGISTER)).isTrue();
        assertThat(codes.isCurrent(email, "other", EmailCodePurpose.REGISTER)).isFalse();
        assertThat(redis.getExpire(prefix + "code")).isBetween(295L, 300L);
        assertThat(redis.getExpire(prefix + "cooldown")).isBetween(55L, 60L);
        assertThat(codes.reserve(email, "654321", "second")).isFalse();
        redis.delete(prefix + "cooldown");
        assertThat(codes.isCurrent(email, "first", EmailCodePurpose.REGISTER)).isTrue();
        assertThat(codes.reserve(email, "012345", "replacement")).isTrue();
        assertThat(codes.isCurrent(email, "first", EmailCodePurpose.REGISTER)).isFalse();
        codes.invalidate(email, "first");
        assertThat(codes.isCurrent(email, "replacement", EmailCodePurpose.REGISTER)).isTrue();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int i = 0; i < 8; i++) tasks.add(() -> codes.consume(email, "012345"));
            int success = 0;
            for (var result : pool.invokeAll(tasks)) if (result.get()) success++;
            assertThat(success).isEqualTo(1);
        } finally { pool.shutdownNow(); }
        redis.delete(prefix + "cooldown");
        assertThat(codes.reserve(email, "012345", "third")).isTrue();
        for (int i = 0; i < 5; i++) assertThat(codes.consume(email, "999999")).isFalse();
        assertThat(codes.consume(email, "012345")).isFalse();
        redis.opsForValue().set(prefix + "code", "012345", Duration.ofMillis(1));
        Thread.sleep(20);
        assertThat(codes.consume(email, "012345")).isFalse();
        redis.delete(prefix + "cooldown");
        assertThat(codes.reserve(email, "123456", "register", EmailCodePurpose.REGISTER)).isTrue();
        assertThat(codes.consume(email, "123456", EmailCodePurpose.LOGIN)).isFalse();
        redis.delete(prefix + "cooldown");
        assertThat(codes.reserve(email, "654321", "login", EmailCodePurpose.LOGIN)).isTrue();
        assertThat(codes.consume(email, "654321", EmailCodePurpose.REGISTER)).isFalse();
        assertThat(codes.consume(email, "654321", EmailCodePurpose.LOGIN)).isTrue();
        assertThat(codes.consume(email, "123456", EmailCodePurpose.REGISTER)).isTrue();
        for (int i = 0; i < 10; i++) limiter.check(email);
        assertThatThrownBy(() -> limiter.check(email)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(429));
    }

    private void refreshCsrf() throws Exception {
        csrf = json.readTree(request("GET", "/csrf-token", Map.of()).body()).at("/data/token").asText();
        assertThat(csrf).isNotBlank();
    }

    private HttpResponse<String> request(String method, String path, Map<String, String> body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        builder.method(method, "POST".equals(method) ? HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
                : HttpRequest.BodyPublishers.noBody());
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
