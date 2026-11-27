package ru.projectx.contracts.service.storage;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.ConsentBinding;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.ReceivedDocuments;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.RequestCancelledException;
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
    public void requireOpen(UUID individualRequestId) {
        if (requests.findById(individualRequestId).filter(IssuanceRequest::cancelled).isPresent()) {
            throw new RequestCancelledException(individualRequestId);
        }
    }

    @Transactional(readOnly = true)
    public Optional<AttachmentResponse> accepted(CertificateUpload upload, String fileHash) {
        return documents.findByAttachmentAttachmentId(upload.attachmentId())
                .map(found -> reuseCertificate(found, upload, fileHash));
    }

    @Transactional(readOnly = true)
    public Optional<ConsentResponse> accepted(ConsentUpload upload, String fileHash) {
        return documents.findByAttachmentAttachmentId(upload.attachmentId())
                .map(found -> reuseConsent(found, upload, fileHash));
    }

    @Transactional
    public AttachmentResponse register(CertificateUpload upload, StoredFile file) {
        requireOpen(upload.individualRequestId());
        PrintedForm certificate = PrintedForm.receivedCertificate(owner(upload), file, clock.instant());
        return AttachmentResponses.of(documents.saveAndFlush(certificate));
    }

    @Transactional
    public ConsentResponse register(ConsentUpload upload, StoredFile file) {
        PrintedForm consent = ReceivedDocuments.consent(
                new ConsentBinding(upload.policyId(), upload.familyMemberId()), upload.attachmentId(), file,
                clock.instant());
        return ConsentResponses.of(documents.saveAndFlush(consent));
    }

    private AttachmentResponse reuseCertificate(PrintedForm found, CertificateUpload upload, String fileHash) {
        if (AttachmentIdentity.sameCertificate(found, upload, fileHash)) {
            return AttachmentResponses.of(found);
        }
        throw new AttachmentConflictException(upload.attachmentId());
    }

    private ConsentResponse reuseConsent(PrintedForm found, ConsentUpload upload, String fileHash) {
        if (AttachmentIdentity.sameConsent(found, upload, fileHash)) {
            return ConsentResponses.of(found);
        }
        throw new AttachmentConflictException(upload.attachmentId());
    }

    private static ParticipantAttachment owner(CertificateUpload upload) {
        return new ParticipantAttachment(upload.individualRequestId(), upload.participantId(), upload.attachmentId());
    }
}
