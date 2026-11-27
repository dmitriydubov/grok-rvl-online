package ru.projectx.contracts.service.storage;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

@Service
public class AttachmentContentService implements AttachmentReads {

    private static final Logger log = LoggerFactory.getLogger(AttachmentContentService.class);

    private final PrintedFormRepository documents;
    private final CertificateStore store;
    private final AttachmentMetrics metrics;
    private final TransactionTemplate transactions;

    public AttachmentContentService(PrintedFormRepository documents, CertificateStore store, AttachmentMetrics metrics,
            TransactionTemplate transactions) {
        this.documents = documents;
        this.store = store;
        this.metrics = metrics;
        this.transactions = transactions;
    }

    @Override
    public OpenedAttachment open(UUID attachmentId) {
        StoredCopy copy = transactions.execute(status -> stored(attachmentId));
        return stream(attachmentId, copy);
    }

    private StoredCopy stored(UUID attachmentId) {
        PrintedForm form = documents.findByAttachmentAttachmentId(attachmentId).filter(AttachmentContentService::active)
                .orElseThrow(AttachmentNotFoundException::new);
        StoredFile file = form.storedFile().orElseThrow(AttachmentNotFoundException::new);
        return new StoredCopy(file.key(), file.contentType(), file.size(), file.hash());
    }

    private OpenedAttachment stream(UUID attachmentId, StoredCopy copy) {
        try {
            return new OpenedAttachment(copy.contentType(), copy.size(), copy.hash(), store.open(copy.key()));
        } catch (CertificateMissingException missing) {
            log.warn("объект вложения {} в хранилище справок не найден", attachmentId);
            throw new AttachmentNotFoundException(missing);
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            throw unavailable;
        }
    }

    private static boolean active(PrintedForm form) {
        return form.attachment().filter(DocumentAttachments::isActive).isPresent();
    }
}
