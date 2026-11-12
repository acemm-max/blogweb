package com.ziv.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.ziv.article.entity.Article;
import com.ziv.article.mapper.ArticleMapper;
import com.ziv.exception.BusinessException;
import com.ziv.util.ErrorLogUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
public class ElasticsearchArticleSearchService implements ArticleSearchService {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchArticleSearchService.class);
    @Autowired @Qualifier("elasticsearchHttp") private ObjectProvider<RestClient> clients;
    @Autowired private SearchProperties properties;
    @Autowired private ArticleMapper articles;

    public List<Article> search(String keyword, int page) {
        if (keyword == null || keyword.isBlank() || keyword.length() > 200 || page < 0 || page > 99) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 400, "Keyword must contain 1-200 characters; page must be 0-99");
        }
        RestClient client = client();
        List<Long> ids = new ArrayList<>();
        try {
            JsonNode response = client.post().uri("/{index}/_search", properties.getIndex())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", page * 20, "size", 20, "_source", false,
                            "query", Map.of("multi_match", Map.of("query", keyword.strip(),
                                    "fields", List.of("title^3", "summary^2", "content")))))
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.path("hits").path("hits").isArray()) {
                throw new IllegalStateException("Invalid Elasticsearch search response");
            }
            for (JsonNode hit : response.path("hits").path("hits")) ids.add(Long.valueOf(hit.path("_id").asText()));
        } catch (RuntimeException exception) {
            throw failure("Elasticsearch keyword search failed", exception);
        }
        if (ids.isEmpty()) return List.of();
        Map<Long, Article> current = articles.selectByIds(ids).stream()
                .filter(article -> Boolean.TRUE.equals(article.getPublished()))
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        // Preserve relevance order, but never expose stale deleted/unpublished documents.
        return ids.stream().map(current::get).filter(java.util.Objects::nonNull).toList();
    }

    public void initializeIndex() {
        RestClient client = client();
        try {
            client.put().uri("/{index}", properties.getIndex()).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0),
                            "mappings", Map.of("dynamic", "strict", "properties", Map.of(
                                    "title", Map.of("type", "text", "analyzer", "cjk"),
                                    "summary", Map.of("type", "text", "analyzer", "cjk"),
                                    "content", Map.of("type", "text", "analyzer", "cjk")))))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest exception) {
            if (!exception.getResponseBodyAsString().contains("resource_already_exists_exception")) {
                throw failure("Elasticsearch index initialization failed", exception);
            }
        } catch (RuntimeException exception) {
            throw failure("Elasticsearch index initialization failed", exception);
        }
    }

    public void synchronize(long articleId) {
        RestClient client = client();
        Article article = articles.selectById(articleId);
        try {
            if (article == null || !Boolean.TRUE.equals(article.getPublished())) {
                try {
                    client.delete().uri("/{index}/_doc/{id}?refresh=wait_for", properties.getIndex(), articleId)
                            .retrieve().toBodilessEntity();
                } catch (HttpClientErrorException.NotFound exception) {
                    // A missing document is already synchronized; a missing index is a configuration error.
                    if (!exception.getResponseBodyAsString().replaceAll("\\s+", "")
                            .contains("\"result\":\"not_found\"")) throw exception;
                }
            } else {
                client.put().uri("/{index}/_doc/{id}?refresh=wait_for", properties.getIndex(), articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("title", article.getTitle(), "summary", article.getSummary() == null ? "" : article.getSummary(),
                                "content", article.getContent())).retrieve().toBodilessEntity();
            }
        } catch (RuntimeException exception) {
            throw failure("Elasticsearch article synchronization failed", exception);
        }
    }

    private RestClient client() {
        RestClient client = clients.getIfAvailable();
        if (client == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 503, "Elasticsearch is not enabled");
        return client;
    }

    public long backfill() {
        initializeIndex();
        Long cursor = null;
        long count = 0;
        while (true) {
            List<Article> batch = articles.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Article>lambdaQuery()
                    .gt(cursor != null, Article::getId, cursor).orderByAsc(Article::getId).last("LIMIT 100"));
            if (batch.isEmpty()) return count;
            for (Article article : batch) {
                synchronize(article.getId());
                cursor = article.getId();
                count++;
            }
        }
    }

    private BusinessException failure(String context, RuntimeException exception) {
        ErrorLogUtil.log(log, context, exception);
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 503, "Article search is unavailable");
    }
}
