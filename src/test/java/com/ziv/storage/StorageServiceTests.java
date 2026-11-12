package com.ziv.storage;

import com.ziv.exception.BusinessException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageServiceTests {
    private final MinioClient client = mock(MinioClient.class);
    private final MinioStorageService service = new MinioStorageService();

    @BeforeEach
    void setup() {
        var beans = new StaticListableBeanFactory(Map.of("minio", client));
        ReflectionTestUtils.setField(service, "clients", beans.getBeanProvider(MinioClient.class));
        ReflectionTestUtils.setField(service, "properties", new StorageProperties());
    }

    @Test
    void uploadUsesServerGeneratedOwnerKeyAndNeutralContentType() throws Exception {
        StoredFile result = service.upload(7L, new MockMultipartFile("file", "../../attack.html", "text/html", new byte[]{1, 2}));
        assertThat(result.getObjectKey()).startsWith("7/").doesNotContain("attack", "..");
        assertThat(result.getSize()).isEqualTo(2);
        var args = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client).putObject(args.capture());
        assertThat(args.getValue().object()).isEqualTo(result.getObjectKey());
        assertThat(args.getValue().contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void anotherUsersObjectCannotBeDownloaded() {
        assertThatThrownBy(() -> service.downloadUrl(8L, "7/4cc7d7c0-c291-46ab-a812-391c768ba4c2"))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(404));
        verifyNoInteractions(client);
    }

    @Test
    void emptyFileIsRejectedBeforeContactingMinio() {
        assertThatThrownBy(() -> service.upload(7L, new MockMultipartFile("file", new byte[0])))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(400));
        verifyNoInteractions(client);
    }

    @Test
    void storageFailureReturns503() throws Exception {
        when(client.putObject(any(PutObjectArgs.class))).thenThrow(new java.io.IOException("connection failed"));
        assertThatThrownBy(() -> service.upload(7L, new MockMultipartFile("file", new byte[]{1})))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(503));
    }
}
