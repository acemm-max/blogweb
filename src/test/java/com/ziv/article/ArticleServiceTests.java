package com.ziv.article;

import com.ziv.article.dto.ArticleRequest;
import com.ziv.article.entity.Article;
import com.ziv.article.mapper.ArticleMapper;
import com.ziv.article.service.impl.ArticleServiceImpl;
import com.ziv.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleServiceTests {
    private final ArticleMapper mapper = mock(ArticleMapper.class);
    private final ArticleServiceImpl service = new ArticleServiceImpl();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "articles", mapper);
    }

    @Test
    void draftIsVisibleOnlyToItsAuthor() {
        Article draft = new Article();
        draft.setId(100L);
        draft.setAuthorId(7L);
        draft.setPublished(false);
        when(mapper.selectById(100L)).thenReturn(draft);
        assertThat(service.get(100L, 7L)).isSameAs(draft);
        assertThatThrownBy(() -> service.get(100L, null)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(404));
        assertThatThrownBy(() -> service.get(100L, 8L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void publishedArticleIsPublic() {
        Article article = new Article();
        article.setPublished(true);
        when(mapper.selectById(100L)).thenReturn(article);
        assertThat(service.get(100L, null)).isSameAs(article);
    }

    @Test
    void creationUsesAuthenticatedAuthorAndReturnsAssignedId() {
        ArticleRequest request = new ArticleRequest();
        request.setTitle(" Title ");
        request.setContent("# Markdown");
        when(mapper.insert(any(Article.class))).thenAnswer(call -> {
            Article article = call.getArgument(0);
            assertThat(article.getAuthorId()).isEqualTo(7L);
            assertThat(article.getCreatedAt()).isNotNull().isEqualTo(article.getUpdatedAt());
            article.setId(100L);
            return 1;
        });
        Article result = service.create(7L, request);
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getTitle()).isEqualTo("Title");
        assertThat(result.getPublished()).isFalse();
    }
}
