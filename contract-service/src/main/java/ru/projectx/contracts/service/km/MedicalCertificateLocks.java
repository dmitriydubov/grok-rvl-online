package ru.projectx.contracts.service.km;

import java.util.List;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;
import ru.projectx.contracts.service.storage.AttachmentGuard;

@Service
public class MedicalCertificateLocks implements AttachmentGuard {

    private final ContractRepository contracts;
    private final OutboxMessageRepository outbox;

    public MedicalCertificateLocks(ContractRepository contracts, OutboxMessageRepository outbox) {
        this.contracts = contracts;
        this.outbox = outbox;
    }

    @Override
    public void ensureRemovable(UUID individualRequestId, UUID participantId) {
        List<Contract> pair = contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(individualRequestId,
                participantId);
        lock(pair);
        if (blocked(pair, individualRequestId)) {
            throw new AttachmentLockedException();
        }
    }

    private void lock(List<Contract> pair) {
        try {
            contracts.lockFresh(pair);
        } catch (PessimisticLockingFailureException busy) {
            throw new AttachmentBusyException(busy);
        }
    }

    private boolean blocked(List<Contract> pair, UUID individualRequestId) {
        if (pair.stream().anyMatch(MedicalCertificateLocks::sentToUsk)) {
            return true;
        }
        return submissionRecorded(individualRequestId, pair);
    }

    private boolean submissionRecorded(UUID individualRequestId, List<Contract> pair) {
        List<UUID> contractIds = pair.stream().map(Contract::id).toList();
        if (contractIds.isEmpty()) {
            return false;
        }
        return outbox.existsByCorrelationIdAndAggregateTypeAndEventTypeAndAggregateIdIn(individualRequestId,
                KmContractKind.KIND.aggregateType(), KmContractKind.KIND.requestEventType(), contractIds);
    }

    private static boolean sentToUsk(Contract contract) {
        return contract.status() == ContractStatus.SUBMITTED || contract.status() == ContractStatus.ISSUED;
    }
}
