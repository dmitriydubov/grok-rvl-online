package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.document.ConsentBinding;
import ru.projectx.contracts.entity.document.ConsentsWithoutFiles;
import ru.projectx.contracts.entity.document.PrintedForm;

class ConsentResponsesTest {

    @Test
    void consentWithoutAFile_isNotAnswered() {
        PrintedForm consent = ConsentsWithoutFiles.bound(new ConsentBinding(
                UUID.fromString("f0e1d2c3-b4a5-4678-9abc-def012345678"),
                UUID.fromString("11111111-2222-4333-8444-555555555555")));

        assertThatThrownBy(() -> ConsentResponses.of(consent)).isInstanceOf(IllegalStateException.class)
                .hasMessage("у согласия null нет файла");
    }
}
