package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import ru.projectx.contracts.service.storage.OpenedAttachment;

class AttachmentContentHttpTest {

    private static final String HASH = "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251";

    @Test
    void unchangedVersion_answersNotModifiedWithTheQuotedEtagAndClosesTheStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        WebRequest request = mock(WebRequest.class);
        when(request.checkNotModified(HASH)).thenReturn(true);

        ResponseEntity<InputStreamResource> response = AttachmentContentHttp.respond(opened(tracking(closed)), request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"" + HASH + "\"");
        assertThat(response.getBody()).isNull();
        assertThat(closed).isTrue();
    }

    private static OpenedAttachment opened(InputStream body) {
        return new OpenedAttachment("image/jpeg", 4, HASH, body);
    }

    private static InputStream tracking(AtomicBoolean closed) {
        return new ByteArrayInputStream(new byte[] {1, 2, 3, 4}) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
    }
}
