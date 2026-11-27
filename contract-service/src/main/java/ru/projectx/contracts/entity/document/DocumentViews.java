package ru.projectx.contracts.entity.document;

import java.util.UUID;

public final class DocumentViews {

    private DocumentViews() {
    }

    public static boolean activeMedical(PrintedForm form, UUID request, UUID participant) {
        return form.docType() == PrintedFormType.MEDICAL_CERTIFICATE && active(form)
                && sameParticipant(form, request, participant);
    }

    public static boolean activeConsent(PrintedForm form, UUID policy, UUID member) {
        return form.docType() == PrintedFormType.PD_CONSENT && active(form) && sameFamily(form, policy, member);
    }

    public static boolean keptForSubmission(PrintedForm form) {
        return form.docType() == PrintedFormType.MEDICAL_CERTIFICATE && active(form);
    }

    public static boolean active(PrintedForm form) {
        return form.removal == null;
    }

    public static boolean sameHash(PrintedForm form, String hash) {
        return form.storedFile().map(StoredFile::hash).filter(hash::equals).isPresent();
    }

    public static ConsentBinding consent(PrintedForm form) {
        if (form.consent == null) {
            throw new IllegalStateException("документ " + form.id() + " — не согласие");
        }
        return form.consent;
    }

    public static UUID attachmentId(PrintedForm form) {
        if (form.attachment == null || form.attachment.attachmentId() == null) {
            throw new IllegalStateException("у документа " + form.id() + " нет вложения");
        }
        return form.attachment.attachmentId();
    }

    private static boolean sameParticipant(PrintedForm form, UUID request, UUID participant) {
        ParticipantAttachment owner = form.attachment;
        return owner != null && request.equals(owner.individualRequestId()) && participant.equals(owner.participantId());
    }

    private static boolean sameFamily(PrintedForm form, UUID policy, UUID member) {
        ConsentBinding owner = form.consent;
        return owner != null && policy.equals(owner.policyId()) && member.equals(owner.familyMemberId());
    }
}
