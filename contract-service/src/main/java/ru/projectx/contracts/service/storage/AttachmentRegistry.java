package ru.projectx.contracts.service.storage;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

@Component
public class AttachmentRegistry {

    private final IssuanceRequestRepository requests;
    private final PrintedFormRepository documents;
    private final Clock clock;

    public AttachmentRegistry(IssuanceRequestRepository requests, PrintedFormRepository documents, Clock clock) {
        this.requests = requests;
        this.documents = documents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public <R> Optional<R> accepted(AttachmentTarget<R> target, String fileHash) {
        Optional<PrintedForm> existing = documents.findByAttachmentAttachmentId(target.attachmentId());
        if (existing.filter(AttachmentRegistry::removed).isPresent()) {
            throw new AttachmentConflictException(target.attachmentId());
        }
        target.checkOpen(requests);
        return existing.map(found -> sameUpload(found, target, fileHash));
    }

    @Transactional
    public <R> R register(AttachmentTarget<R> target, StoredFile file) {
        target.checkOpen(requests);
        PrintedForm attachment = PrintedForm.receivedAttachment(target.docType(), target.owner(), file,
                clock.instant());
        return target.response(documents.saveAndFlush(attachment));
    }

    private static boolean removed(PrintedForm form) {
        return form.attachment().filter(DocumentAttachments::isActive).isEmpty();
    }

    private static <R> R sameUpload(PrintedForm existing, AttachmentTarget<R> target, String fileHash) {
        if (!matchesOwner(existing, target) || !matchesContent(existing, fileHash)) {
            throw new AttachmentConflictException(target.attachmentId());
        }
        return target.response(existing);
    }

    private static boolean matchesOwner(PrintedForm existing, AttachmentTarget<?> target) {
        return existing.docType() == target.docType()
                && existing.attachment().filter(target.owner()::equals).isPresent();
    }

    private static boolean matchesContent(PrintedForm existing, String fileHash) {
        return existing.storedFile().map(StoredFile::hash).filter(fileHash::equals).isPresent();
    }
}
