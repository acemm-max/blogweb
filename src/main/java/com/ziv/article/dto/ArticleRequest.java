package com.ziv.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ArticleRequest {
    @NotBlank @Size(max = 200)
    private String title;
    @Size(max = 500)
    private String summary;
    @NotBlank @Size(max = 1000000)
    private String content;
    @NotNull
    private Boolean published = false;
}
