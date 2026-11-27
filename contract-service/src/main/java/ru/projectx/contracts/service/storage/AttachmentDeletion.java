package ru.projectx.contracts.service.storage;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.document.DocumentAttachment;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.service.RequestIds;

@Service
public class AttachmentDeletion implements AttachmentDeletes {

    private final PrintedFormRepository documents;
    private final AttachmentGuard guard;
    private final CertificateStore store;
    private final AttachmentMetrics metrics;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public AttachmentDeletion(PrintedFormRepository documents, AttachmentGuard guard, CertificateStore store,
            AttachmentMetrics metrics, TransactionTemplate transactions, Clock clock) {
        this.documents = documents;
        this.guard = guard;
        this.store = store;
        this.metrics = metrics;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public void delete(UUID attachmentId) {
        Optional<Removal> removal = transactions.execute(status -> prepare(attachmentId));
        if (removal.isEmpty()) {
            return;
        }
        Removal plan = removal.orElseThrow();
        erase(plan);
        transactions.executeWithoutResult(status -> finish(plan));
    }

    private Optional<Removal> prepare(UUID attachmentId) {
        Optional<PrintedForm> found = documents.findByAttachmentAttachmentId(attachmentId)
                .filter(AttachmentDeletion::active);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PrintedForm form = found.orElseThrow();
        hold(form);
        return Optional.of(planned(form));
    }

    private void hold(PrintedForm form) {
        if (form.docType() != PrintedFormType.MEDICAL_CERTIFICATE) {
            return;
        }
        DocumentAttachment attachment = form.attachment().orElseThrow();
        guard.ensureRemovable(attachment.individualRequestId(), attachment.participantId());
    }

    private void erase(Removal plan) {
        RuntimeException failure = removalFailure(plan);
        if (failure instanceof StorageUnavailableException) {
            metrics.countStorageUnavailable();
            throw failure;
        }
    }

    private RuntimeException removalFailure(Removal plan) {
        try {
            store.remove(plan.key());
            return null;
        } catch (CertificateMissingException | StorageUnavailableException failure) {
            return failure;
        }
    }

    private void finish(Removal plan) {
        if (plan.type() == PrintedFormType.MEDICAL_CERTIFICATE) {
            guard.ensureRemovable(plan.individualRequestId(), plan.participantId());
        }
        documents.markAttachmentDeleted(plan.attachmentId(), clock.instant(), RequestIds.current());
    }

    private static boolean active(PrintedForm form) {
        return form.attachment().filter(DocumentAttachments::isActive).isPresent();
    }

    private static Removal planned(PrintedForm form) {
        DocumentAttachment attachment = form.attachment().orElseThrow();
        StoredFile file = form.storedFile().orElseThrow();
        return new Removal(attachment.attachmentId(), attachment.individualRequestId(), attachment.participantId(),
                form.docType(), file.key());
    }
}
