package ru.projectx.contracts.entity.document;

import java.time.Instant;
import java.util.UUID;

public final class ReceivedDocuments {

    private ReceivedDocuments() {
    }

    public static PrintedForm consent(ConsentBinding owner, UUID attachmentId, StoredFile file, Instant now) {
        return new PrintedForm(new ParticipantAttachment(null, null, attachmentId), owner, file,
                PrintedFormType.PD_CONSENT, now);
    }

    public static PrintedForm removed(ParticipantAttachment attachment, StoredFile file, Instant createdAt,
            DocumentRemoval removal) {
        PrintedForm form = PrintedForm.receivedCertificate(attachment, file, createdAt);
        form.removal = removal;
        return form;
    }
}
