package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.ReceivedDocuments;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

class AttachmentRegistryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final StoredFile FILE = new StoredFile("certificates/f/" + Uploads.ATTACHMENT + ".jpg",
            Uploads.CONTENT_HASH, 76, "image/jpeg");

    private final IssuanceRequestRepository requests = mock(IssuanceRequestRepository.class);
    private final PrintedFormRepository documents = mock(PrintedFormRepository.class);
    private final AttachmentRegistry registry = new AttachmentRegistry(requests, documents,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void unknownRequest_staysOpenAndHasNoAcceptedRecord() {
        when(requests.findById(Uploads.REQUEST)).thenReturn(Optional.empty());
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        registry.requireOpen(Uploads.REQUEST);

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).isEmpty();
    }

    @Test
    void participantOutsideTheComposition_hasNoAcceptedRecord() {
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).isEmpty();
    }

    @Test
    void cancelledRequest_isRequestCancelledEvenWithoutParticipants() {
        knownRequest(true);

        assertThatThrownBy(() -> registry.requireOpen(Uploads.REQUEST))
                .isInstanceOf(RequestCancelledException.class).hasMessageContaining(Uploads.REQUEST.toString());
    }

    @Test
    void newAttachmentOfAParticipant_hasNoAcceptedRecord() {
        knownRequest(false, Uploads.OTHER_PARTICIPANT, Uploads.PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).isEmpty();
    }

    @Test
    void sameAttachmentWithSameContent_isThePriorRecord() {
        knownRequest(false, Uploads.PARTICIPANT);
        PrintedForm prior = certificate(Uploads.PARTICIPANT, Uploads.CONTENT_HASH);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(prior));

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).get()
                .returns(Uploads.ATTACHMENT, AttachmentResponse::attachmentId)
                .returns(NOW.minusSeconds(300), AttachmentResponse::createdAt);
    }

    @Test
    void sameAttachmentWithOtherContent_isConflict() {
        knownRequest(false, Uploads.PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.PARTICIPANT, "0".repeat(64))));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class).hasMessageContaining(Uploads.ATTACHMENT.toString());
    }

    @Test
    void sameAttachmentOfAnotherParticipant_isConflict() {
        knownRequest(false, Uploads.PARTICIPANT, Uploads.OTHER_PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.OTHER_PARTICIPANT, Uploads.CONTENT_HASH)));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void register_savesTheCertificateOfTheParticipantAtTheCurrentInstant() {
        knownRequest(false, Uploads.PARTICIPANT);
        when(documents.saveAndFlush(any(PrintedForm.class))).thenAnswer(saved -> saved.getArgument(0));

        AttachmentResponse response = registry.register(Uploads.jpeg(), FILE);

        ArgumentCaptor<PrintedForm> saved = ArgumentCaptor.forClass(PrintedForm.class);
        verify(documents).saveAndFlush(saved.capture());
        assertThat(saved.getValue().attachment())
                .contains(new ParticipantAttachment(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT));
        assertThat(saved.getValue().storedFile()).contains(FILE);
        assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
        assertThat(response.fileHash()).isEqualTo(Uploads.CONTENT_HASH);
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void requestCancelledWhileTheFileWasStored_isNotRegistered() {
        knownRequest(true, Uploads.PARTICIPANT);

        assertThatThrownBy(() -> registry.register(Uploads.jpeg(), FILE)).isInstanceOf(RequestCancelledException.class);

        verify(documents, never()).saveAndFlush(any());
    }

    @Test
    void registerWithoutAKnownRequest_savesTheCertificate() {
        when(requests.findById(Uploads.REQUEST)).thenReturn(Optional.empty());
        when(documents.saveAndFlush(any(PrintedForm.class))).thenAnswer(saved -> saved.getArgument(0));

        registry.register(Uploads.jpeg(), FILE);

        verify(documents).saveAndFlush(any(PrintedForm.class));
    }

    @Test
    void sameBytesWithAnotherContentType_keepTheStoredType() {
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.PARTICIPANT, Uploads.CONTENT_HASH)));

        assertThat(registry.accepted(Uploads.of(Uploads.PARTICIPANT, "image/png", Uploads.CONTENT),
                Uploads.CONTENT_HASH)).get().returns("image/jpeg", AttachmentResponse::contentType);
    }

    @Test
    void certificateAgainstAStoredConsent_isConflict() {
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(
                ReceivedDocuments.consent(new ru.projectx.contracts.entity.document.ConsentBinding(UUID.randomUUID(),
                        UUID.randomUUID()), Uploads.ATTACHMENT, FILE, NOW)));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void removedCertificateWithTheSameBytes_isConflict() {
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(ReceivedDocuments.removed(
                new ParticipantAttachment(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT), FILE,
                NOW.minusSeconds(300), new ru.projectx.contracts.entity.document.DocumentRemoval(NOW,
                        "a41f8c02-7d3b-4e19-9a55-2c7e0b6f1d84"))));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void sameConsent_returnsThePriorRecord() {
        UUID policy = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        ConsentUpload upload = new ConsentUpload(policy, member, Uploads.ATTACHMENT, "image/png", FILE.size(),
                Uploads.jpeg().content());
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(
                ReceivedDocuments.consent(new ru.projectx.contracts.entity.document.ConsentBinding(policy, member),
                        Uploads.ATTACHMENT, FILE, NOW.minusSeconds(300))));

        assertThat(registry.accepted(upload, Uploads.CONTENT_HASH)).get()
                .returns("image/jpeg", ConsentResponse::contentType)
                .returns(policy, ConsentResponse::policyId)
                .returns(member, ConsentResponse::familyMemberId);
    }

    private void knownRequest(boolean cancelled, UUID... participants) {
        IssuanceRequest request = IssuanceRequest.start(Uploads.REQUEST, NOW);
        request.takeComposition(Arrays.stream(participants)
                .map(participant -> new RequestParticipant(participant, DocumentKind.LM_POLICY)).toList(), NOW);
        if (cancelled) {
            request.cancel(NOW);
        }
        when(requests.findById(Uploads.REQUEST)).thenReturn(Optional.of(request));
    }

    private static PrintedForm certificate(UUID participant, String hash) {
        return PrintedForm.receivedCertificate(new ParticipantAttachment(Uploads.REQUEST, participant, Uploads.ATTACHMENT),
                new StoredFile(FILE.key(), hash, FILE.size(), FILE.contentType()), NOW.minusSeconds(300));
    }
}
