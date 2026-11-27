package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.storage.AttachmentDeletes;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import ru.projectx.contracts.service.storage.AttachmentReads;
import ru.projectx.contracts.service.storage.AttachmentUploads;
import ru.projectx.contracts.service.storage.OpenedAttachment;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class AttachmentExchangeControllerTest {

    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final String HASH = "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251";
    private static final byte[] CONTENT = new byte[] {(byte) 0xFF, 0x00, 0x10, 0x7F};
    private static final String TRACE = "a41f8c02-7d3b-4e19-9a55-2c7e0b6f1d84";
    private static final String CONTENT_PATH = "/internal/v1/attachments/{attachment}/content";
    private static final String DELETE_PATH = "/internal/v1/attachments/{attachment}";

    private final AttachmentReads reads = mock(AttachmentReads.class);
    private final AttachmentDeletes deletions = mock(AttachmentDeletes.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AttachmentController(mock(AttachmentUploads.class),
            reads, deletions))
            .setControllerAdvice(new RequestFormatAdvice(new AttachmentMetrics(new SimpleMeterRegistry())),
                    new DomainErrorAdvice(), new UnexpectedErrorAdvice())
            .setMessageConverters(new ResourceHttpMessageConverter(), new JacksonJsonHttpMessageConverter(JsonMapper
                    .builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()))
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void content_streamsTheBytesWithTypeLengthAndQuotedEtag() throws Exception {
        when(reads.open(ATTACHMENT)).thenReturn(opened(stream()));

        mvc.perform(get(CONTENT_PATH, ATTACHMENT).header(RequestIds.HEADER, TRACE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(CONTENT.length)))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + HASH + "\""))
                .andExpect(header().string(RequestIds.HEADER, TRACE))
                .andExpect(content().bytes(CONTENT));
    }

    @Test
    void content_closesTheStreamWhenTheCallerAlreadyHasThisVersion() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        when(reads.open(ATTACHMENT)).thenReturn(opened(tracking(closed)));

        mvc.perform(get(CONTENT_PATH, ATTACHMENT).header(HttpHeaders.IF_NONE_MATCH, "\"" + HASH + "\""))
                .andExpect(status().isNotModified());

        assertThat(closed).isTrue();
    }

    @Test
    void content_closesTheStreamWhenWritingFails() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        when(reads.open(ATTACHMENT)).thenReturn(opened(failing(closed)));

        mvc.perform(get(CONTENT_PATH, ATTACHMENT)).andExpect(status().is5xxServerError());

        assertThat(closed).isTrue();
    }

    @Test
    void content_ofANonUuid_isAValidationErrorOnThePathField() throws Exception {
        mvc.perform(get(CONTENT_PATH, "not-a-guid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value("attachment_id"));
        verifyNoInteractions(reads);
    }

    @Test
    void delete_answersNoContentWithAnEmptyBody() throws Exception {
        mvc.perform(delete(DELETE_PATH, ATTACHMENT).header(RequestIds.HEADER, TRACE))
                .andExpect(status().isNoContent())
                .andExpect(header().string(RequestIds.HEADER, TRACE))
                .andExpect(content().string(""));

        verify(deletions).delete(ATTACHMENT);
    }

    @Test
    void delete_ofANonUuid_isAValidationErrorOnThePathField() throws Exception {
        mvc.perform(delete(DELETE_PATH, "not-a-guid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("attachment_id"));
        verifyNoInteractions(deletions);
    }

    @Test
    void missingLockedBusyAndUnavailable_useTheDomainCodesWithoutFieldDetails() throws Exception {
        when(reads.open(ATTACHMENT)).thenThrow(new AttachmentNotFoundException())
                .thenThrow(new AttachmentLockedException())
                .thenThrow(new AttachmentBusyException(new IllegalStateException("busy")))
                .thenThrow(new StorageUnavailableException(new IOException("down")));

        expect(404, "ATTACHMENT_NOT_FOUND");
        expect(409, "ATTACHMENT_LOCKED");
        expect(503, "SERVICE_UNAVAILABLE");
        expect(503, "STORAGE_UNAVAILABLE");
    }

    private void expect(int status, String code) throws Exception {
        mvc.perform(get(CONTENT_PATH, ATTACHMENT))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.details.length()").value(0));
    }

    private static OpenedAttachment opened(InputStream body) {
        return new OpenedAttachment("image/jpeg", CONTENT.length, HASH, body);
    }

    private static InputStream stream() {
        return new ByteArrayInputStream(CONTENT);
    }

    private static InputStream tracking(AtomicBoolean closed) {
        return new ByteArrayInputStream(CONTENT) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
    }

    private static InputStream failing(AtomicBoolean closed) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("запись оборвалась");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
    }
}
