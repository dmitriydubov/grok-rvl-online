package ru.projectx.contracts.controller;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.context.request.WebRequest;
import ru.projectx.contracts.service.storage.OpenedAttachment;

final class AttachmentContentHttp {

    private AttachmentContentHttp() {
    }

    static ResponseEntity<InputStreamResource> respond(OpenedAttachment opened, WebRequest request) throws IOException {
        if (request.checkNotModified(opened.hash())) {
            opened.body().close();
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(opened.hash()).build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(opened.contentType()))
                .contentLength(opened.size()).eTag(opened.hash()).body(new InputStreamResource(opened.body()));
    }
}
