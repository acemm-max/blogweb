package com.ziv.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StoredFile {
    private String objectKey;
    private long size;
}
