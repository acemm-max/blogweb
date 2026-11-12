package com.ziv.search;

import com.ziv.article.entity.Article;
import java.util.List;

public interface ArticleSearchService {
    List<Article> search(String keyword, int page);
    void initializeIndex();
    void synchronize(long articleId);
    long backfill();
}
