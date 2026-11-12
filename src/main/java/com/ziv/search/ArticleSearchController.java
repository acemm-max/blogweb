package com.ziv.search;

import com.ziv.article.entity.Article;
import com.ziv.common.ApiResponse;
import com.ziv.common.ApiResponseUtil;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class ArticleSearchController {
    @Autowired private ArticleSearchService search;

    @GetMapping("/api/articles/search")
    public ApiResponse<List<Article>> search(@RequestParam String keyword, @RequestParam(defaultValue = "0") int page) {
        return ApiResponseUtil.success(search.search(keyword, page));
    }

    @PostMapping("/api/admin/search/index")
    public ApiResponse<Void> initialize() {
        search.initializeIndex();
        return ApiResponseUtil.success();
    }

    @PostMapping("/api/admin/search/backfill")
    public ApiResponse<java.util.Map<String, Long>> backfill() {
        return ApiResponseUtil.success(java.util.Map.of("processed", search.backfill()));
    }

    @PostMapping("/api/admin/search/articles/{id}")
    public ApiResponse<Void> synchronize(@PathVariable long id) {
        search.synchronize(id);
        return ApiResponseUtil.success();
    }
}
