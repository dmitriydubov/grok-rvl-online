package ru.projectx.contracts.acceptance;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record AttachmentRequest(
        String request,
        String participant,
        Optional<String> attachmentPart,
        Optional<FilePart> filePart,
        Optional<String> requestId,
        boolean multipart,
        Optional<String> attachmentFileName,
        boolean omitFileName,
        boolean consent) {

    static final String BOUNDARY = "stand-attachment-boundary";

    record FilePart(String fileName, @Nullable String contentType, byte[] content) {
    }

    static AttachmentRequest certificate(String request, String participant, Optional<String> attachmentPart,
            Optional<FilePart> filePart, Optional<String> requestId, boolean multipart) {
        return new AttachmentRequest(request, participant, attachmentPart, filePart, requestId, multipart,
                Optional.empty(), false, false);
    }

    AttachmentRequest withRequestId(String value) {
        return new AttachmentRequest(request, participant, attachmentPart, filePart, Optional.of(value), multipart,
                attachmentFileName, omitFileName, consent);
    }

    AttachmentRequest withAttachmentFileName(String fileName) {
        return new AttachmentRequest(request, participant, attachmentPart, filePart, requestId, multipart,
                Optional.of(fileName), omitFileName, consent);
    }

    AttachmentRequest withoutFileName() {
        return new AttachmentRequest(request, participant, attachmentPart, filePart, requestId, multipart,
                attachmentFileName, true, consent);
    }

    AttachmentRequest withFileName(String fileName) {
        FilePart file = filePart.orElseThrow(() -> new AssertionError("в запросе нет части file"));
        return new AttachmentRequest(request, participant, attachmentPart,
                Optional.of(new FilePart(fileName, file.contentType(), file.content())), requestId, multipart,
                attachmentFileName, omitFileName, consent);
    }

    AttachmentRequest asConsent() {
        return new AttachmentRequest(request, participant, attachmentPart, filePart, requestId, multipart,
                attachmentFileName, omitFileName, true);
    }

    String path() {
        if (consent) {
            return "/internal/v1/policies/" + request + "/family-members/" + participant + "/consents";
        }
        return "/internal/v1/requests/" + request + "/participants/" + participant + "/attachments";
    }

    String contentType() {
        return multipart ? "multipart/form-data; boundary=" + BOUNDARY : "application/json";
    }

    byte[] body() {
        if (!multipart) {
            return ("{\"attachment_id\":\"" + attachmentPart.orElse("") + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeAttachment(body);
        filePart.ifPresent(file -> writeFile(body, "file", file, omitFileName));
        text(body, "--" + BOUNDARY + "--\r\n");
        return body.toByteArray();
    }

    private void writeAttachment(ByteArrayOutputStream body) {
        if (attachmentFileName.isPresent()) {
            writeFile(body, "attachment_id", new FilePart(attachmentFileName.get(), "text/plain",
                    attachmentPart.orElse("").getBytes(StandardCharsets.UTF_8)), false);
            return;
        }
        attachmentPart.ifPresent(value -> text(body, "--" + BOUNDARY + "\r\nContent-Disposition: form-data; "
                + "name=\"attachment_id\"\r\n\r\n" + value + "\r\n"));
    }

    private static void writeFile(ByteArrayOutputStream body, String name, FilePart file, boolean omitName) {
        String disposition = omitName ? "name=\"" + name + "\"" : "name=\"" + name + "\"; filename=\""
                + file.fileName() + "\"";
        text(body, "--" + BOUNDARY + "\r\nContent-Disposition: form-data; " + disposition + "\r\n"
                + Optional.ofNullable(file.contentType()).map(type -> "Content-Type: " + type + "\r\n").orElse("")
                + "\r\n");
        body.writeBytes(file.content());
        text(body, "\r\n");
    }

    private static void text(ByteArrayOutputStream body, String text) {
        body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
