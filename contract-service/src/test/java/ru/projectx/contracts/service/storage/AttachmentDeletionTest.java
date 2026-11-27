package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.contracts.entity.document.DocumentAttachment;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.support.BindingTransactions;

class AttachmentDeletionTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final StoredFile FILE = new StoredFile("certificates/0b6c/" + ATTACHMENT + ".jpg", "ab", 4,
            "image/jpeg");

    private final PrintedFormRepository documents = mock(PrintedFormRepository.class);
    private final AttachmentGuard guard = mock(AttachmentGuard.class);
    private final CertificateStore store = mock(CertificateStore.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AttachmentDeletion deletion = new AttachmentDeletion(documents, guard, store,
            new AttachmentMetrics(meters), BindingTransactions.template(), Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearRequestId() {
        MDC.remove(RequestIds.MDC_KEY);
    }

    @Test
    void delete_ofAnUnknownOrAlreadyDeletedAttachment_doesNotTouchTheStorage() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(deleted()));

        deletion.delete(ATTACHMENT);
        deletion.delete(ATTACHMENT);

        verifyNoInteractions(store, guard);
        verify(documents, never()).markAttachmentDeleted(any(), any(), any());
        assertThat(meters.find("contracts.storage.unavailable").counter()).isNull();
    }

    @Test
    void delete_ofACertificate_removesTheObjectOutsideTheTransactionAndMarksTheRow() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(certificate()));
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(store).remove(FILE.key());
        MDC.put(RequestIds.MDC_KEY, "k16b-del");

        deletion.delete(ATTACHMENT);

        InOrder order = inOrder(guard, store, documents);
        order.verify(guard).ensureRemovable(REQUEST, PARTICIPANT);
        order.verify(store).remove(FILE.key());
        order.verify(guard).ensureRemovable(REQUEST, PARTICIPANT);
        order.verify(documents).markAttachmentDeleted(ATTACHMENT, NOW, "k16b-del");
        assertThat(meters.find("contracts.storage.unavailable").counter()).isNull();
    }

    @Test
    void delete_ofAConsent_doesNotLockContracts() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(consent()));
        MDC.put(RequestIds.MDC_KEY, "k16b-consent");

        deletion.delete(ATTACHMENT);

        verifyNoInteractions(guard);
        verify(store).remove(FILE.key());
        verify(documents).markAttachmentDeleted(ATTACHMENT, NOW, "k16b-consent");
    }

    @Test
    void delete_ofALockedCertificate_leavesTheObjectAndTheRow() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(certificate()));
        doThrow(new AttachmentLockedException()).when(guard).ensureRemovable(REQUEST, PARTICIPANT);

        assertThatThrownBy(() -> deletion.delete(ATTACHMENT)).isInstanceOf(AttachmentLockedException.class);
        verifyNoInteractions(store);
        verify(documents, never()).markAttachmentDeleted(any(), any(), any());
    }

    @Test
    void delete_whenTheContractRowIsBusy_leavesTheObjectAndTheRow() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(certificate()));
        doThrow(new AttachmentBusyException(new IllegalStateException("busy"))).when(guard).ensureRemovable(REQUEST,
                PARTICIPANT);

        assertThatThrownBy(() -> deletion.delete(ATTACHMENT)).isInstanceOf(AttachmentBusyException.class);
        verifyNoInteractions(store);
        verify(documents, never()).markAttachmentDeleted(any(), any(), any());
    }

    @Test
    void delete_whenTheObjectIsAlreadyGone_stillMarksTheRow() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(certificate()));
        doThrow(new CertificateMissingException(new IOException("NoSuchKey"))).when(store).remove(FILE.key());
        MDC.put(RequestIds.MDC_KEY, "k16b-gone");

        deletion.delete(ATTACHMENT);

        verify(documents).markAttachmentDeleted(ATTACHMENT, NOW, "k16b-gone");
        assertThat(meters.find("contracts.storage.unavailable").counter()).isNull();
    }

    @Test
    void delete_whenTheStorageIsDown_keepsTheRowActive() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(certificate()));
        StorageUnavailableException unavailable = new StorageUnavailableException(new IOException("down"));
        doThrow(unavailable).when(store).remove(FILE.key());

        assertThatThrownBy(() -> deletion.delete(ATTACHMENT)).isSameAs(unavailable);
        verify(documents, never()).markAttachmentDeleted(any(), any(), any());
        assertThat(meters.get("contracts.storage.unavailable").counter().count()).isEqualTo(1);
    }

    @Test
    void delete_lockedOnTheSecondCheck_doesNotMarkTheRow() {
        when(documents.findByAttachmentAttachmentId(ATTACHMENT)).thenReturn(Optional.of(certificate()));
        doNothing().doThrow(new AttachmentLockedException()).when(guard).ensureRemovable(REQUEST, PARTICIPANT);

        assertThatThrownBy(() -> deletion.delete(ATTACHMENT)).isInstanceOf(AttachmentLockedException.class);
        verify(store).remove(FILE.key());
        verify(documents, never()).markAttachmentDeleted(any(), any(), any());
    }

    private static PrintedForm certificate() {
        return PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE,
                DocumentAttachments.certificate(REQUEST, PARTICIPANT, ATTACHMENT), FILE, NOW);
    }

    private static PrintedForm consent() {
        return PrintedForm.receivedAttachment(PrintedFormType.PD_CONSENT,
                DocumentAttachments.consent(UUID.randomUUID(), UUID.randomUUID(), ATTACHMENT), FILE, NOW);
    }

    private static PrintedForm deleted() {
        return PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE,
                new DocumentAttachment(REQUEST, PARTICIPANT, null, null, ATTACHMENT, NOW, "req-1"), FILE, NOW);
    }
}
