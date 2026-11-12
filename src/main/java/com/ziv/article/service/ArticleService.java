package com.ziv.article.service;

import com.ziv.article.dto.ArticleRequest;
import com.ziv.article.entity.Article;
import java.util.List;

public interface ArticleService {
    Article create(long authorId, ArticleRequest request);
    Article get(long id, Long viewerId);
    List<Article> list(Long beforeId);
    Article update(long id, long authorId, ArticleRequest request);
    void delete(long id, long authorId);
}
