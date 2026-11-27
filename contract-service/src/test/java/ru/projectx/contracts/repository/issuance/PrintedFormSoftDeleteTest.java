package ru.projectx.contracts.repository.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.document.DocumentAttachment;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class PrintedFormSoftDeleteTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T15:40:00Z");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final StoredFile FILE = new StoredFile("certificates/0b6c/" + ATTACHMENT + ".jpg", "ab", 4,
            "image/jpeg");

    @Autowired
    private PrintedFormRepository documents;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void empty() {
        jdbc.sql("truncate table contract_documents cascade").update();
    }

    @Test
    void markDeleted_updatesOnlyTheActiveRowAndClearsTheSession() {
        documents.saveAndFlush(PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE,
                DocumentAttachments.certificate(UUID.randomUUID(), UUID.randomUUID(), ATTACHMENT), FILE, NOW));

        Integer updated = transaction.execute(status -> {
            documents.findByAttachmentAttachmentId(ATTACHMENT).orElseThrow();
            int rows = documents.markAttachmentDeleted(ATTACHMENT, NOW, "k16b-first");
            assertThat(documents.findByAttachmentAttachmentId(ATTACHMENT).orElseThrow().attachment().orElseThrow()
                    .deletedAt()).isEqualTo(NOW);
            return rows;
        });

        assertThat(updated).isEqualTo(1);
        Integer repeated = transaction.execute(status -> documents.markAttachmentDeleted(ATTACHMENT, LATER, "k16b-second"));
        assertThat(repeated).isZero();
        DocumentAttachment attachment = documents.findByAttachmentAttachmentId(ATTACHMENT).orElseThrow().attachment()
                .orElseThrow();
        assertThat(attachment.deletedAt()).isEqualTo(NOW);
        assertThat(attachment.deletedRequestId()).isEqualTo("k16b-first");
        Integer unknown = transaction.execute(status -> documents.markAttachmentDeleted(UUID.randomUUID(), LATER,
                "k16b-unknown"));
        assertThat(unknown).isZero();
    }
}
