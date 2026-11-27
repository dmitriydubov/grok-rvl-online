package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;

class MedicalCertificateLocksTest {

    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final UUID CONTRACT = UUID.fromString("0b6c1d2e-7f3a-4b5c-9d8e-1f2a3b4c5d6e");

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final OutboxMessageRepository outbox = mock(OutboxMessageRepository.class);
    private final MedicalCertificateLocks locks = new MedicalCertificateLocks(contracts, outbox);

    @Test
    void noContractYet_isRemovableWithoutReadingTheOutbox() {
        when(contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(REQUEST, PARTICIPANT))
                .thenReturn(List.of());

        assertThatCode(() -> locks.ensureRemovable(REQUEST, PARTICIPANT)).doesNotThrowAnyException();

        verify(contracts).lockFresh(List.of());
        verify(outbox, never()).existsByCorrelationIdAndAggregateTypeAndEventTypeAndAggregateIdIn(any(), any(), any(),
                any());
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"SUBMITTED", "ISSUED"})
    void contractSentToUsk_isLockedWithoutTheOutbox(ContractStatus status) {
        Contract sent = contract(status);
        when(contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(REQUEST, PARTICIPANT))
                .thenReturn(List.of(sent));

        assertThatThrownBy(() -> locks.ensureRemovable(REQUEST, PARTICIPANT)).isInstanceOf(AttachmentLockedException.class);
        verify(outbox, never()).existsByCorrelationIdAndAggregateTypeAndEventTypeAndAggregateIdIn(any(), any(), any(),
                any());
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"DRAFT_READY", "ACCEPTED", "INVOICE_ISSUED", "PAID", "ERROR",
            "CANCELLED"})
    void contractWithoutASubmissionRecord_isRemovable(ContractStatus status) {
        Contract open = contract(status);
        when(contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(REQUEST, PARTICIPANT))
                .thenReturn(List.of(open));
        when(outbox.existsByCorrelationIdAndAggregateTypeAndEventTypeAndAggregateIdIn(REQUEST,
                KmContractKind.KIND.aggregateType(), KmContractKind.KIND.requestEventType(), List.of(CONTRACT)))
                .thenReturn(false);

        assertThatCode(() -> locks.ensureRemovable(REQUEST, PARTICIPANT)).doesNotThrowAnyException();
    }

    @Test
    void submissionRecord_locksEvenAfterTheContractLeftTheSentState() {
        Contract failed = contract(ContractStatus.ERROR);
        Contract paid = contract(ContractStatus.PAID);
        when(contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(REQUEST, PARTICIPANT))
                .thenReturn(List.of(failed, paid));
        when(outbox.existsByCorrelationIdAndAggregateTypeAndEventTypeAndAggregateIdIn(REQUEST, "CONTRACT",
                "KmContractSubmitted", List.of(CONTRACT, CONTRACT))).thenReturn(true);

        assertThatThrownBy(() -> locks.ensureRemovable(REQUEST, PARTICIPANT)).isInstanceOf(AttachmentLockedException.class);
    }

    @Test
    void lockTimeout_isBusyAndDoesNotReadTheOutbox() {
        Contract paid = contract(ContractStatus.PAID);
        when(contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(REQUEST, PARTICIPANT))
                .thenReturn(List.of(paid));
        org.mockito.Mockito.doThrow(new CannotAcquireLockException("busy")).when(contracts).lockFresh(any());

        assertThatThrownBy(() -> locks.ensureRemovable(REQUEST, PARTICIPANT)).isInstanceOf(AttachmentBusyException.class)
                .hasCauseInstanceOf(CannotAcquireLockException.class);
        verify(outbox, never()).existsByCorrelationIdAndAggregateTypeAndEventTypeAndAggregateIdIn(any(), any(), any(),
                any());
    }

    private static Contract contract(ContractStatus status) {
        Contract contract = mock(Contract.class);
        when(contract.id()).thenReturn(CONTRACT);
        when(contract.status()).thenReturn(status);
        return contract;
    }
}
