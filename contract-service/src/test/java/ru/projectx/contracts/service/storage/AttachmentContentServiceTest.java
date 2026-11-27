package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.support.BindingTransactions;

class AttachmentContentServiceTest {

    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final StoredFile FILE = new StoredFile("certificates/0b6c/" + ATTACHMENT + ".jpg", "ab", 4,
            "image/jpeg");
    private static final byte[] CONTENT = new byte[] {(byte) 0xFF, 0x01};

    private final PrintedFormRepository documents = mock(PrintedFormRepository.class);
    private final CertificateStore store = mock(CertificateStore.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AttachmentContentService content = new AttachmentContentService(documents, store,
            new AttachmentMetrics(meters), BindingTransactions.template());

    @Test
    void open_readsOutsideTheDatabaseTransaction() throws IOException {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(active()));
        when(store.open(FILE.key())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ByteArrayInputStream(CONTENT);
        });

        OpenedAttachment opened = content.open(ATTACHMENT);

        assertThat(opened.contentType()).isEqualTo("image/jpeg");
        assertThat(opened.size()).isEqualTo(4);
        assertThat(opened.hash()).isEqualTo("ab");
        assertThat(opened.body().readAllBytes()).isEqualTo(CONTENT);
        assertThat(meters.find("contracts.storage.unavailable").counter()).isNull();
    }

    @Test
    void open_ofAnUnknownOrDeletedAttachment_doesNotTouchTheStorage() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(deleted()));

        assertThatThrownBy(() -> content.open(ATTACHMENT)).isInstanceOf(AttachmentNotFoundException.class);
        assertThatThrownBy(() -> content.open(ATTACHMENT)).isInstanceOf(AttachmentNotFoundException.class);
        verifyNoInteractions(store);
        assertThat(meters.find("contracts.storage.unavailable").counter()).isNull();
    }

    @Test
    void open_ofAMissingObject_warnsWithoutCountingStorageFailure() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(active()));
        when(store.open(FILE.key())).thenThrow(new CertificateMissingException(new IOException("NoSuchKey")));

        assertThatThrownBy(() -> content.open(ATTACHMENT)).isInstanceOf(AttachmentNotFoundException.class);
        assertThat(meters.find("contracts.storage.unavailable").counter()).isNull();
        verify(store).open(FILE.key());
    }

    @Test
    void open_whenTheStorageIsDown_countsAndRethrows() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(active()));
        StorageUnavailableException unavailable = new StorageUnavailableException(new IOException("down"));
        when(store.open(FILE.key())).thenThrow(unavailable);

        assertThatThrownBy(() -> content.open(ATTACHMENT)).isSameAs(unavailable);
        assertThat(meters.get("contracts.storage.unavailable").counter().count()).isEqualTo(1);
    }

    private static PrintedForm active() {
        return PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE,
                DocumentAttachments.certificate(UUID.randomUUID(), UUID.randomUUID(), ATTACHMENT), FILE,
                java.time.Instant.parse("2026-02-10T07:05:00Z"));
    }

    private static PrintedForm deleted() {
        return PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE,
                new ru.projectx.contracts.entity.document.DocumentAttachment(UUID.randomUUID(), UUID.randomUUID(), null,
                        null, ATTACHMENT, java.time.Instant.parse("2026-02-10T07:05:00Z"), "req-1"),
                FILE, java.time.Instant.parse("2026-02-10T07:05:00Z"));
    }
}
