package ru.projectx.contracts.service.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;

class MinioObjectStoreTest {

    private static final byte[] CONTENT = new byte[] {(byte) 0xD0, (byte) 0xA1, (byte) 0xFF, 0x00};
    private static final String KEY = "certificates/0b6c/3c2f.jpg";

    private final MinioClient client = mock(MinioClient.class);
    private final MinioCertificateStore store = new MinioCertificateStore(client, "rvl-contracts");

    @Test
    void open_readsTheObjectBytesAndClosesTheResponseStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream raw = new ByteArrayInputStream(CONTENT) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        when(client.getObject(any())).thenReturn(response(raw));

        try (InputStream opened = store.open(KEY)) {
            assertThat(opened.readAllBytes()).isEqualTo(CONTENT);
        }

        ArgumentCaptor<GetObjectArgs> read = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(client).getObject(read.capture());
        assertThat(read.getValue().bucket()).isEqualTo("rvl-contracts");
        assertThat(read.getValue().object()).isEqualTo(KEY);
        assertThat(closed).isTrue();
    }

    @Test
    void open_ofAMissingKeyInALiveBucket_isCertificateMissing() throws Exception {
        ErrorResponseException missing = noSuchKey();
        when(client.getObject(any())).thenThrow(missing);
        when(client.bucketExists(any())).thenReturn(true);

        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(CertificateMissingException.class).hasCause(missing);
        verify(client).bucketExists(any(BucketExistsArgs.class));
    }

    @Test
    void open_ofAMissingKeyWhenTheBucketIsGoneOrCannotBeChecked_isStorageUnavailable() throws Exception {
        when(client.getObject(any())).thenThrow(noSuchKey());
        when(client.bucketExists(any())).thenReturn(false).thenThrow(new IOException("refused"));

        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class).hasCauseInstanceOf(
                ErrorResponseException.class);
        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class).hasCauseInstanceOf(
                IOException.class);
    }

    @Test
    void open_whenTheStorageRefusesOrFails_isStorageUnavailable() throws Exception {
        when(client.getObject(any())).thenThrow(noSuchBucket()).thenThrow(new InvalidResponseException(404, "", "", ""))
                .thenThrow(new ServerException("503", 503, "trace"))
                .thenThrow(new IOException("refused"))
                .thenThrow(new RuntimeException(new InterruptedException()));

        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(InvalidResponseException.class);
        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(ServerException.class);
        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(RuntimeException.class);
        verify(client, never()).bucketExists(any());
    }

    @Test
    void remove_deletesTheObjectInTheBucket() throws Exception {
        store.remove(KEY);

        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(removed.capture());
        assertThat(removed.getValue().bucket()).isEqualTo("rvl-contracts");
        assertThat(removed.getValue().object()).isEqualTo(KEY);
    }

    @Test
    void remove_ofAMissingKeyInALiveBucket_isCertificateMissing() throws Exception {
        ErrorResponseException missing = noSuchKey();
        doThrow(missing).when(client).removeObject(any());
        when(client.bucketExists(any())).thenReturn(true);

        assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(CertificateMissingException.class).hasCause(missing);
    }

    @Test
    void remove_whenTheStorageRefusesOrFails_isStorageUnavailable() throws Exception {
        doThrow(noSuchKey()).doThrow(noSuchBucket())
                .doThrow(new InvalidResponseException(404, "", null, "trace"))
                .doThrow(new ServerException("503", 503, "trace"))
                .doThrow(new IOException("refused"))
                .doThrow(new RuntimeException(new InterruptedException()))
                .when(client).removeObject(any());
        when(client.bucketExists(any())).thenReturn(false);

        assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(InvalidResponseException.class);
        assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(ServerException.class);
        assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private static GetObjectResponse response(InputStream raw) {
        return new GetObjectResponse(Headers.of(), "rvl-contracts", "us-east-1", KEY, raw);
    }

    private static ErrorResponseException noSuchKey() {
        return new ErrorResponseException(
                new ErrorResponse("NoSuchKey", "Object does not exist", "rvl-contracts", KEY, null, null, null), null,
                null);
    }

    private static ErrorResponseException noSuchBucket() {
        return new ErrorResponseException(
                new ErrorResponse("NoSuchBucket", "The specified bucket does not exist", "rvl-contracts", null, null,
                        null, null),
                null, null);
    }
}
