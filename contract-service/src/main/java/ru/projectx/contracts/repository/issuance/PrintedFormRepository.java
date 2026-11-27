package ru.projectx.contracts.repository.issuance;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;

public interface PrintedFormRepository extends JpaRepository<PrintedForm, UUID> {

    List<PrintedForm> findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(Collection<UUID> lmPolicyIds,
            Collection<UUID> contractIds);

    Optional<PrintedForm> findByAttachmentAttachmentId(UUID attachmentId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PrintedForm form
               set form.attachment.deletedAt = :deletedAt,
                   form.attachment.deletedRequestId = :deletedRequestId
             where form.attachment.attachmentId = :attachmentId
               and form.attachment.deletedAt is null
            """)
    int markAttachmentDeleted(@Param("attachmentId") UUID attachmentId, @Param("deletedAt") Instant deletedAt,
            @Param("deletedRequestId") String deletedRequestId);

    List<PrintedForm> findByAttachmentIndividualRequestIdAndAttachmentParticipantIdAndDocTypeAndAttachmentDeletedAtIsNullOrderByCreatedAtAscIdAsc(
            UUID individualRequestId, UUID participantId, PrintedFormType docType);
}
