package com.ziv.article.controller;

import com.ziv.article.dto.ArticleRequest;
import com.ziv.article.entity.Article;
import com.ziv.article.service.ArticleService;
import com.ziv.common.ApiResponse;
import com.ziv.common.ApiResponseUtil;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    @Autowired
    private ArticleService articles;

    @GetMapping
    public ApiResponse<List<Article>> list(@RequestParam(required = false) Long beforeId) {
        return ApiResponseUtil.success(articles.list(beforeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Article> get(@PathVariable long id, Authentication authentication) {
        Long viewer = authentication == null || authentication instanceof AnonymousAuthenticationToken
                ? null : Long.valueOf(authentication.getName());
        return ApiResponseUtil.success(articles.get(id, viewer));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Article> create(@Valid @RequestBody ArticleRequest request, Authentication authentication) {
        return ApiResponseUtil.success(articles.create(Long.parseLong(authentication.getName()), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Article> update(@PathVariable long id, @Valid @RequestBody ArticleRequest request,
            Authentication authentication) {
        return ApiResponseUtil.success(articles.update(id, Long.parseLong(authentication.getName()), request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Authentication authentication) {
        articles.delete(id, Long.parseLong(authentication.getName()));
    }
}
