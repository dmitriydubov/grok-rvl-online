package ru.projectx.contracts.service.inbox.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.databind.json.JsonMapper;

class OptionalFieldTest {

    private static final String BODY = """
            {"data": {"id": "61a7982c-a0b4-4bbf-9527-60b40b752803", "date": "2026-04-10", "amount": 34000.00,
                      "name": "Тестов", "number": 5, "nothing": null,
                      "beyondInt": 99999999999, "beyondLong": 99999999999999999999}}
            """;

    private final JsonFields data = new JsonFields(JsonMapper.builder().build().readTree(BODY), "")
            .requiredObject("data");

    @Test
    void presentTextValues_areReadFromTheirFields() {
        assertThat(data.field("name").optional().text()).isEqualTo("Тестов");
        assertThat(data.field("id").optional().uuid()).isEqualTo(UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"));
        assertThat(data.field("date").optional().date()).isEqualTo(LocalDate.parse("2026-04-10"));
    }

    @Test
    void absentOptionalValues_areNull() {
        JsonFields fields = nodes();
        assertThat(fields.field("missing").optional().text()).isNull();
        assertThat(fields.field("nothing").optional().text()).isNull();
        assertThat(fields.field("missing").optional().uuid()).isNull();
        assertThat(fields.field("nothing").optional().uuid()).isNull();
        assertThat(fields.field("missing").optional().date()).isNull();
        assertThat(fields.field("nothing").optional().date()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"zero", "five", "fraction", "yes", "no", "emptyArray", "array", "emptyObject", "object"})
    void nonTextOptionalValues_areViolationsWithoutTheValueOrCause(String name) {
        JsonFields fields = nodes();
        String message = "недопустимое значение поля data." + name;
        assertThatThrownBy(() -> fields.field(name).optional().text()).isInstanceOf(ContractViolationException.class)
                .hasMessage(message).hasNoCause();
        assertThatThrownBy(() -> fields.field(name).optional().uuid()).hasMessage(message).hasNoCause();
        assertThatThrownBy(() -> fields.field(name).optional().date()).hasMessage(message).hasNoCause();
    }

    @Test
    void blankStrings_stayTextAndFailGuidAndDateFormat() {
        JsonFields fields = nodes();
        assertThat(fields.field("blank").optional().text()).isEmpty();
        assertThat(fields.field("spaces").optional().text()).isEqualTo("   ");
        assertThatThrownBy(() -> fields.field("blank").optional().uuid())
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.blank").hasNoCause();
        assertThatThrownBy(() -> fields.field("spaces").optional().uuid())
                .hasMessage("недопустимое значение поля data.spaces").hasNoCause();
        assertThatThrownBy(() -> fields.field("blank").optional().date())
                .hasMessage("недопустимое значение поля data.blank").hasCauseInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> fields.field("spaces").optional().date())
                .hasMessage("недопустимое значение поля data.spaces").hasCauseInstanceOf(DateTimeParseException.class);
    }

    private static JsonFields nodes() {
        String body = """
                {"data": {"nothing": null, "blank": "", "spaces": "   ", "zero": 0, "five": 5, "fraction": 12.34,
                          "yes": true, "no": false, "emptyArray": [], "array": ["вымышленная"], "emptyObject": {},
                          "object": {"fio": "вымышленная"}}}
                """;
        return new MessageBodies(JsonMapper.builder().build()).data(body);
    }

    @Test
    void malformedTextValues_areViolationsWithoutTheValue() {
        assertThatThrownBy(() -> data.field("name").optional().uuid()).isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.name");
        assertThatThrownBy(() -> data.field("name").optional().date())
                .hasMessage("недопустимое значение поля data.name").hasCauseInstanceOf(DateTimeParseException.class);
    }

    @Test
    void optionalLongField_isNullWhenAbsentOrNullAndOtherwiseAWholeNumberThatFitsLong() {
        assertThat(data.field("missing").optional().longInteger()).isNull();
        assertThat(data.field("nothing").optional().longInteger()).isNull();
        assertThat(data.field("number").optional().longInteger()).isEqualTo(5L);
        assertThat(data.field("beyondInt").optional().longInteger()).isEqualTo(99999999999L);
        assertThatThrownBy(() -> data.field("amount").optional().longInteger())
                .isInstanceOf(ContractViolationException.class).hasMessage("недопустимое значение поля data.amount");
        assertThatThrownBy(() -> data.field("beyondLong").optional().longInteger())
                .hasMessage("недопустимое значение поля data.beyondLong");
        assertThatThrownBy(() -> data.field("name").optional().longInteger())
                .hasMessage("недопустимое значение поля data.name");
    }
}
