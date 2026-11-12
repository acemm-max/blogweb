package com.ziv.article.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ziv.article.dto.ArticleRequest;
import com.ziv.article.entity.Article;
import com.ziv.article.mapper.ArticleMapper;
import com.ziv.article.service.ArticleService;
import com.ziv.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ArticleServiceImpl implements ArticleService {
    @Autowired
    private ArticleMapper articles;

    public Article create(long authorId, ArticleRequest request) {
        Article article = new Article();
        article.setAuthorId(authorId);
        apply(article, request);
        article.setCreatedAt(article.getUpdatedAt());
        articles.insert(article);
        return article;
    }

    public Article get(long id, Long viewerId) {
        Article article = articles.selectById(id);
        if (article == null || (!Boolean.TRUE.equals(article.getPublished())
                && !java.util.Objects.equals(article.getAuthorId(), viewerId))) throw notFound();
        return article;
    }

    public List<Article> list(Long beforeId) {
        return articles.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getPublished, true).lt(beforeId != null, Article::getId, beforeId)
                .orderByDesc(Article::getId).last("LIMIT 20"));
    }

    public Article update(long id, long authorId, ArticleRequest request) {
        Article changes = new Article();
        apply(changes, request);
        int changed = articles.update(changes, Wrappers.<Article>lambdaUpdate()
                .eq(Article::getId, id).eq(Article::getAuthorId, authorId));
        if (changed == 0) throw notFound();
        return get(id, authorId);
    }

    public void delete(long id, long authorId) {
        if (articles.delete(Wrappers.<Article>lambdaQuery().eq(Article::getId, id)
                .eq(Article::getAuthorId, authorId)) == 0) throw notFound();
    }

    private void apply(Article article, ArticleRequest request) {
        article.setTitle(request.getTitle().strip());
        article.setSummary(request.getSummary() == null ? "" : request.getSummary().strip());
        article.setContent(request.getContent());
        article.setPublished(request.getPublished());
        article.setUpdatedAt(LocalDateTime.now());
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, 404, "Article not found or inaccessible");
    }
}
