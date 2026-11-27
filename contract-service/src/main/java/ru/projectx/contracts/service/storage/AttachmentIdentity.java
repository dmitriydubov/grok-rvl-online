package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.entity.document.DocumentViews;
import ru.projectx.contracts.entity.document.PrintedForm;

public final class AttachmentIdentity {

    private AttachmentIdentity() {
    }

    public static boolean sameCertificate(PrintedForm form, CertificateUpload upload, String hash) {
        return DocumentViews.activeMedical(form, upload.individualRequestId(), upload.participantId())
                && DocumentViews.sameHash(form, hash);
    }

    public static boolean sameConsent(PrintedForm form, ConsentUpload upload, String hash) {
        return DocumentViews.activeConsent(form, upload.policyId(), upload.familyMemberId())
                && DocumentViews.sameHash(form, hash);
    }
}
