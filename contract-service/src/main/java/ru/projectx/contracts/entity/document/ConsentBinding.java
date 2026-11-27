package ru.projectx.contracts.entity.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record ConsentBinding(
        @Column(name = "policy_id", updatable = false) UUID policyId,
        @Column(name = "family_member_id", updatable = false) UUID familyMemberId) {
}
