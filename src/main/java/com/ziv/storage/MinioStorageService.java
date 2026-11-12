package com.ziv.storage;

import com.ziv.exception.BusinessException;
import com.ziv.util.ErrorLogUtil;
import io.minio.*;
import io.minio.http.Method;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MinioStorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);
    @Autowired private ObjectProvider<MinioClient> clients;
    @Autowired private StorageProperties properties;

    public StoredFile upload(long userId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 400, "File must be non-empty and no larger than 10 MiB");
        }
        MinioClient client = client();
        String key = userId + "/" + UUID.randomUUID();
        try (InputStream stream = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(key)
                    .stream(stream, file.getSize(), -1).contentType("application/octet-stream").build());
            return new StoredFile(key, file.getSize());
        } catch (Exception exception) {
            throw failure("MinIO upload failed", exception);
        }
    }

    public String downloadUrl(long userId, String objectKey) {
        String prefix = userId + "/";
        if (objectKey == null || !objectKey.startsWith(prefix)
                || !objectKey.substring(prefix.length()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 404, "File not found or inaccessible");
        }
        MinioClient client = client();
        try {
            client.statObject(StatObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build());
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(properties.getBucket()).object(objectKey)
                    .expiry(properties.getUrlExpirySeconds())
                    .extraQueryParams(Map.of("response-content-disposition", "attachment")).build());
        } catch (io.minio.errors.ErrorResponseException exception) {
            ErrorLogUtil.log(log, "MinIO download lookup failed", exception);
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new BusinessException(HttpStatus.NOT_FOUND, 404, "File not found");
            }
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 503, "Object storage is unavailable");
        } catch (Exception exception) {
            throw failure("MinIO download URL generation failed", exception);
        }
    }

    private MinioClient client() {
        MinioClient client = clients.getIfAvailable();
        if (client == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 503, "MinIO is not enabled");
        return client;
    }

    private BusinessException failure(String context, Exception exception) {
        ErrorLogUtil.log(log, context, exception);
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 503, "Object storage is unavailable");
    }
}
