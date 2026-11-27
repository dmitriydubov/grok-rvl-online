package ru.projectx.contracts.controller;

import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.service.storage.AttachmentDeletes;
import ru.projectx.contracts.service.storage.AttachmentReads;
import ru.projectx.contracts.service.storage.AttachmentUploads;
import ru.projectx.contracts.service.storage.CertificateUpload;

@InternalApi
public class AttachmentController {

    private final AttachmentUploads uploads;
    private final AttachmentReads reads;
    private final AttachmentDeletes deletions;

    public AttachmentController(AttachmentUploads uploads, AttachmentReads reads, AttachmentDeletes deletions) {
        this.uploads = uploads;
        this.reads = reads;
        this.deletions = deletions;
    }

    @PostMapping(path = "/requests/{individual_request_id}/participants/{participant_id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse upload(
            @PathVariable("individual_request_id") UUID individualRequestId,
            @PathVariable("participant_id") UUID participantId,
            @RequestParam("attachment_id") UUID attachmentId,
            @RequestPart("file") MultipartFile file) {
        return uploads.receive(new CertificateUpload(individualRequestId, participantId, attachmentId,
                MultipartParts.requireFile(file)));
    }

    @GetMapping("/attachments/{attachment_id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable("attachment_id") UUID attachmentId,
            WebRequest request) throws IOException {
        return AttachmentContentHttp.respond(reads.open(attachmentId), request);
    }

    @DeleteMapping("/attachments/{attachment_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("attachment_id") UUID attachmentId) {
        deletions.delete(attachmentId);
    }
}
