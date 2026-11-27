package ru.projectx.contracts.controller;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.service.storage.AttachmentUploads;
import ru.projectx.contracts.service.storage.CertificateUpload;
import ru.projectx.contracts.service.storage.ConsentUpload;

@InternalApi
public class AttachmentController {

    private final AttachmentUploads uploads;

    public AttachmentController(AttachmentUploads uploads) {
        this.uploads = uploads;
    }

    @PostMapping(path = "/requests/{individual_request_id}/participants/{participant_id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse upload(
            @PathVariable("individual_request_id") UUID individualRequestId,
            @PathVariable("participant_id") UUID participantId,
            MultipartHttpServletRequest request) {
        UploadParts parts = UploadParts.of(request);
        MultipartFile file = parts.file();
        return uploads.receive(new CertificateUpload(individualRequestId, participantId, parts.attachmentId(),
                file.getContentType(), file.getSize(), file::getInputStream));
    }

    @PostMapping(path = "/policies/{policy_id}/family-members/{family_member_id}/consents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConsentResponse consent(
            @PathVariable("policy_id") UUID policyId,
            @PathVariable("family_member_id") UUID familyMemberId,
            MultipartHttpServletRequest request) {
        UploadParts parts = UploadParts.of(request);
        MultipartFile file = parts.file();
        return uploads.receive(new ConsentUpload(policyId, familyMemberId, parts.attachmentId(),
                file.getContentType(), file.getSize(), file::getInputStream));
    }
}
