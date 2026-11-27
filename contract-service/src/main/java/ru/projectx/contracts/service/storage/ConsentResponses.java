package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.ConsentBinding;
import ru.projectx.contracts.entity.document.DocumentViews;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;

final class ConsentResponses {

    private ConsentResponses() {
    }

    static ConsentResponse of(PrintedForm consent) {
        ConsentBinding owner = DocumentViews.consent(consent);
        StoredFile file = consent.storedFile().orElseThrow(
                () -> new IllegalStateException("у согласия " + consent.id() + " нет файла"));
        return new ConsentResponse(consent.id(), DocumentViews.attachmentId(consent), owner.policyId(),
                owner.familyMemberId(), file.contentType(), file.size(), file.hash(), consent.createdAt());
    }
}
