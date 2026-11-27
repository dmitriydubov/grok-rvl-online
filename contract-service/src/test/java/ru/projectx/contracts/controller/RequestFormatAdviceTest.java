package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.projectx.contracts.dto.ErrorResponse;
import ru.projectx.contracts.service.storage.AttachmentMetrics;

class RequestFormatAdviceTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RequestFormatAdvice advice = new RequestFormatAdvice(new AttachmentMetrics(meters));

    @Test
    void missingParameter_isValidationErrorOfThatFieldAndIsNotARejectedFile() {
        ResponseEntity<ErrorResponse> response = advice.missingParameter(
                new MissingServletRequestParameterException("policy_id", "UUID"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).satisfies(body -> assertThat(body.error().details()).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.field()).isEqualTo("policy_id");
                    assertThat(violation.reason()).isEqualTo("обязательное поле не передано");
                }));
        assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }

    @Test
    void missingPart_isValidationErrorOfThatPartAndIsNotARejectedFile() {
        ResponseEntity<ErrorResponse> response = advice.missingPart(new MissingServletRequestPartException("file"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).satisfies(body -> assertThat(body.error().details()).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.field()).isEqualTo("file");
                    assertThat(violation.reason()).isEqualTo("обязательное поле не передано");
                }));
        assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }
}
