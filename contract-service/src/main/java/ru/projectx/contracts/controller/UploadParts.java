package ru.projectx.contracts.controller;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

final class UploadParts {

    static final String ATTACHMENT = "attachment_id";
    static final String FILE = "file";

    private final UUID attachmentId;
    private final MultipartFile file;

    private UploadParts(UUID attachmentId, MultipartFile file) {
        this.attachmentId = attachmentId;
        this.file = file;
    }

    static UploadParts of(MultipartHttpServletRequest request) {
        return new UploadParts(attachmentId(request), file(request));
    }

    UUID attachmentId() {
        return attachmentId;
    }

    MultipartFile file() {
        return file;
    }

    private static UUID attachmentId(MultipartHttpServletRequest request) {
        if (request.getFile(ATTACHMENT) != null) {
            throw new UploadFormatException(ATTACHMENT, RequestFormatAdvice.INVALID_FORMAT);
        }
        return parse(request.getParameter(ATTACHMENT));
    }

    private static UUID parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UploadFormatException(ATTACHMENT, RequestFormatAdvice.MISSING);
        }
        return uuid(raw);
    }

    private static UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException bad) {
            throw new UploadFormatException(ATTACHMENT, RequestFormatAdvice.INVALID_FORMAT, bad);
        }
    }

    private static MultipartFile file(MultipartHttpServletRequest request) {
        MultipartFile file = request.getFile(FILE);
        if (file == null) {
            throw new UploadFormatException(FILE, RequestFormatAdvice.MISSING);
        }
        requireName(file);
        return file;
    }

    private static void requireName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new UploadFormatException(FILE, RequestFormatAdvice.INVALID_FORMAT);
        }
    }
}
