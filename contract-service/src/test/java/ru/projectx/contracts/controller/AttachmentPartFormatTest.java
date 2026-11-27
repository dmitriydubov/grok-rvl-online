package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import ru.projectx.contracts.service.storage.AttachmentUploads;
import ru.projectx.contracts.service.storage.ConsentUpload;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class AttachmentPartFormatTest {

    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    private static final UUID POLICY = UUID.fromString("f0e1d2c3-b4a5-4678-9abc-def012345678");
    private static final UUID MEMBER = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String ATTACHMENT = "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21";
    private static final String CERTIFICATE = "/internal/v1/requests/{request}/participants/{participant}/attachments";
    private static final String CONSENT = "/internal/v1/policies/{policy}/family-members/{member}/consents";
    private static final byte[] CONTENT = "СТЕНД".getBytes(StandardCharsets.UTF_8);

    private final AttachmentUploads uploads = mock(AttachmentUploads.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AttachmentController(uploads))
            .setControllerAdvice(new RequestFormatAdvice(new AttachmentMetrics(meters)), new DomainErrorAdvice(),
                    new UnexpectedErrorAdvice())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()))
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"id.txt", ""})
    void attachmentIdWithAFileName_isValidationErrorOnBothUploads(String fileName) throws Exception {
        MockMultipartFile named = new MockMultipartFile("attachment_id", fileName, "text/plain", ATTACHMENT.getBytes());
        MockMultipartFile file = new MockMultipartFile("file", "Тестова_Анна_070у.jpg", "image/jpeg", CONTENT);

        mvc.perform(multipart(CERTIFICATE, REQUEST, PARTICIPANT).file(named).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("attachment_id"));
        mvc.perform(multipart(CONSENT, POLICY, MEMBER).file(named).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("attachment_id"));

        verifyNoInteractions(uploads);
        org.assertj.core.api.Assertions.assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void fileWithoutAName_isValidationErrorOnTheCertificate(String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "image/jpeg", CONTENT);

        mvc.perform(multipart(CERTIFICATE, REQUEST, PARTICIPANT).file(file).param("attachment_id", ATTACHMENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"));

        verifyNoInteractions(uploads);
        org.assertj.core.api.Assertions.assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }

    @Test
    void consentPathThatIsNotAUuid_namesThePathField() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "согласие.jpg", "image/jpeg", CONTENT);

        mvc.perform(multipart(CONSENT, "not-a-guid", MEMBER).file(file).param("attachment_id", ATTACHMENT)
                        .header(RequestIds.HEADER, "trace"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("policy_id"));
        mvc.perform(multipart(CONSENT, POLICY, "not-a-guid").file(file).param("attachment_id", ATTACHMENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("family_member_id"));

        verifyNoInteractions(uploads);
    }

    @Test
    void consent_passesThePolicyTheMemberAndTheFile() throws Exception {
        when(uploads.receive(any(ConsentUpload.class))).thenReturn(new ConsentResponse(
                UUID.fromString("0b6c1d2e-7f3a-4b5c-9d8e-1f2a3b4c5d6e"), UUID.fromString(ATTACHMENT), POLICY, MEMBER,
                "image/jpeg", CONTENT.length, "ab", Instant.parse("2026-02-10T07:05:00Z")));

        mvc.perform(multipart(CONSENT, POLICY, MEMBER)
                        .file(new MockMultipartFile("file", "согласие.jpg", "image/jpeg", CONTENT))
                        .param("attachment_id", ATTACHMENT).header(RequestIds.HEADER, "trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy_id").value(POLICY.toString()))
                .andExpect(jsonPath("$.family_member_id").value(MEMBER.toString()))
                .andExpect(jsonPath("$.content_type").value("image/jpeg"));

        ArgumentCaptor<ConsentUpload> received = ArgumentCaptor.forClass(ConsentUpload.class);
        verify(uploads).receive(received.capture());
        ConsentUpload upload = received.getValue();
        assertThat(upload.policyId()).isEqualTo(POLICY);
        assertThat(upload.familyMemberId()).isEqualTo(MEMBER);
        assertThat(upload.attachmentId()).isEqualTo(UUID.fromString(ATTACHMENT));
        try (InputStream stream = upload.content().open()) {
            assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
        }
    }
}
