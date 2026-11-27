package ru.projectx.contracts.entity.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentViewsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final UUID POLICY = UUID.fromString("f0e1d2c3-b4a5-4678-9abc-def012345678");
    private static final UUID MEMBER = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final StoredFile FILE = new StoredFile("certificates/f/" + ATTACHMENT + ".jpg",
            "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251", 76, "image/jpeg");

    @Test
    void storedConsent_answersWithItsBindingAndAttachment() {
        PrintedForm consent = ReceivedDocuments.consent(new ConsentBinding(POLICY, MEMBER), ATTACHMENT, FILE, NOW);

        assertThat(DocumentViews.consent(consent)).isEqualTo(new ConsentBinding(POLICY, MEMBER));
        assertThat(DocumentViews.attachmentId(consent)).isEqualTo(ATTACHMENT);
    }

    @Test
    void certificate_isNotAConsent() {
        PrintedForm certificate = PrintedForm.receivedCertificate(
                new ParticipantAttachment(REQUEST, PARTICIPANT, ATTACHMENT), FILE, NOW);

        assertThatThrownBy(() -> DocumentViews.consent(certificate)).isInstanceOf(IllegalStateException.class)
                .hasMessage("документ null — не согласие");
    }

    @Test
    void attachmentWithoutAnId_isNotAnAttachment() {
        PrintedForm certificate = PrintedForm.receivedCertificate(
                new ParticipantAttachment(REQUEST, PARTICIPANT, null), FILE, NOW);

        assertThatThrownBy(() -> DocumentViews.attachmentId(certificate)).isInstanceOf(IllegalStateException.class)
                .hasMessage("у документа null нет вложения");
    }
}
