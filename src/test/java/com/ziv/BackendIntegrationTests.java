package com.ziv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziv.article.mapper.ArticleMapper;
import com.ziv.search.ArticleSearchService;
import com.ziv.storage.StorageService;
import com.ziv.storage.StorageProperties;
import com.ziv.user.entity.User;
import com.ziv.user.mapper.UserMapper;
import io.minio.*;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "BLOGWEB_FULL_INTEGRATION", matches = "true")
class BackendIntegrationTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired ArticleMapper articles;
    @Autowired ArticleSearchService search;
    @Autowired StorageService storage;
    @Autowired StorageProperties storageProperties;
    @Autowired MinioClient minio;
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private String csrf;

    @Test
    void loginPublishCanalSearchUpdateDeleteAndMinioRoundTrip() throws Exception {
        search.initializeIndex();
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(storageProperties.getBucket()).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(storageProperties.getBucket()).build());
        }
        String unique = "integration" + UUID.randomUUID().toString().replace("-", "");
        User user = new User();
        user.setEmail(unique + "@qq.com"); user.setPassword("TestPassword123!");
        user.setNickname("Integration"); user.setRole("USER"); user.setStatus(1);
        users.insert(user);
        User other = new User();
        other.setEmail("other" + unique + "@qq.com"); other.setPassword("TestPassword123!");
        other.setNickname("Other"); other.setRole("USER"); other.setStatus(1);
        users.insert(other);
        Long articleId = null;
        String objectKey = null;
        try {
            refreshCsrf();
            var login = request("POST", "/api/sessions/loginbypassword", Map.of("email", user.getEmail(), "password", user.getPassword()));
            assertThat(login.statusCode()).isEqualTo(201);
            assertThat(request("GET", "/api/users/me", null).statusCode()).isEqualTo(200);
            refreshCsrf();
            var created = request("POST", "/api/articles", Map.of("title", unique, "summary", "Java博客", "content", "Spring Boot Redis", "published", true));
            assertThat(created.statusCode()).isEqualTo(201);
            articleId = Long.valueOf(json.readTree(created.body()).at("/data/id").asText());
            final long id = articleId;
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                    assertThat(search.search(unique, 0)).extracting(com.ziv.article.entity.Article::getId).contains(id));

            assertThat(request("POST", "/api/sessions/loginbypassword", Map.of("email", other.getEmail(), "password", other.getPassword())).statusCode()).isEqualTo(201);
            refreshCsrf();
            assertThat(request("PUT", "/api/articles/" + id, Map.of("title", "unauthorized", "content", "replacement", "published", true)).statusCode()).isEqualTo(404);
            assertThat(request("DELETE", "/api/articles/" + id, null).statusCode()).isEqualTo(404);
            assertThat(articles.selectById(id).getTitle()).isEqualTo(unique);
            assertThat(request("POST", "/api/admin/search/backfill", null).statusCode()).isEqualTo(403);
            assertThat(request("POST", "/api/sessions/loginbypassword", Map.of("email", user.getEmail(), "password", user.getPassword())).statusCode()).isEqualTo(201);
            refreshCsrf();

            assertThat(request("PUT", "/api/articles/" + id, Map.of("title", unique, "content", "updated", "published", false)).statusCode()).isEqualTo(200);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                String response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:29200/blogweb-integration-articles/_doc/" + id)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                assertThat(json.readTree(response).path("found").asBoolean()).isFalse();
            });
            assertThat(search.backfill()).isGreaterThanOrEqualTo(1);
            assertThat(request("DELETE", "/api/articles/" + id, null).statusCode()).isEqualTo(204);
            articleId = null;
            assertThat(articles.selectById(id)).isNull();

            var stored = storage.upload(user.getId(), new MockMultipartFile("file", "test.txt", "text/plain", "minio-round-trip".getBytes()));
            objectKey = stored.getObjectKey();
            String signed = storage.downloadUrl(user.getId(), objectKey);
            var download = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(signed)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(download.statusCode()).isEqualTo(200);
            assertThat(download.body()).isEqualTo("minio-round-trip");
            final String key = objectKey;
            assertThatThrownBy(() -> storage.downloadUrl(other.getId(), key))
                    .isInstanceOfSatisfying(com.ziv.exception.BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(404));
            assertThat(request("DELETE", "/api/sessions/logout", null).statusCode()).isEqualTo(204);
            assertThat(request("GET", "/api/users/me", null).statusCode()).isEqualTo(401);
        } finally {
            if (articleId != null) articles.deleteById(articleId);
            if (objectKey != null) minio.removeObject(RemoveObjectArgs.builder().bucket(storageProperties.getBucket()).object(objectKey).build());
            users.deleteById(user.getId());
            users.deleteById(other.getId());
        }
    }

    private void refreshCsrf() throws Exception {
        var response = request("GET", "/api/csrf-token", null);
        assertThat(response.statusCode()).isEqualTo(200);
        csrf = json.readTree(response.body()).at("/data/token").asText();
    }

    private HttpResponse<String> request(String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
