package com.ziv.search;

import com.ziv.article.entity.Article;
import com.ziv.article.mapper.ArticleMapper;
import com.ziv.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ArticleSearchTests {
    private final ArticleMapper articles = mock(ArticleMapper.class);
    private final ElasticsearchArticleSearchService service = new ElasticsearchArticleSearchService();
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("http://localhost:9200");
        server = MockRestServiceServer.bindTo(builder).build();
        var beans = new StaticListableBeanFactory(Map.of("elasticsearchHttp", builder.build()));
        ReflectionTestUtils.setField(service, "clients", beans.getBeanProvider(RestClient.class));
        ReflectionTestUtils.setField(service, "properties", new SearchProperties());
        ReflectionTestUtils.setField(service, "articles", articles);
    }

    @Test
    void excludesStaleDraftAndDeletedHitsAndPreservesRelevance() {
        server.expect(requestTo("http://localhost:9200/blogweb-articles/_search"))
                .andExpect(method(HttpMethod.POST)).andExpect(jsonPath("$.query.multi_match.query").value("Java"))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[{\"_id\":\"3\"},{\"_id\":\"2\"},{\"_id\":\"1\"}]}}", MediaType.APPLICATION_JSON));
        Article published = new Article(); published.setId(1L); published.setPublished(true);
        Article draft = new Article(); draft.setId(2L); draft.setPublished(false);
        when(articles.selectByIds(List.of(3L, 2L, 1L))).thenReturn(List.of(published, draft));
        assertThat(service.search(" Java ", 0)).containsExactly(published);
        server.verify();
    }

    @Test
    void draftChangeDeletesDocumentInsteadOfIndexingItsContent() {
        Article draft = new Article(); draft.setPublished(false);
        when(articles.selectById(2L)).thenReturn(draft);
        server.expect(requestTo("http://localhost:9200/blogweb-articles/_doc/2?refresh=wait_for"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        service.synchronize(2L);
        server.verify();
    }

    @Test
    void deletingAnAlreadyAbsentDocumentIsIdempotent() {
        when(articles.selectById(2L)).thenReturn(null);
        server.expect(requestTo("http://localhost:9200/blogweb-articles/_doc/2?refresh=wait_for"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body("{ \"result\": \"not_found\" }"));
        service.synchronize(2L);
        server.verify();
    }

    @Test
    void rejectsEmptyKeywordBeforeAccessingDependencies() {
        assertThatThrownBy(() -> service.search(" ", 0)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(400));
        verifyNoInteractions(articles);
        server.verify();
    }
}
