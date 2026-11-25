package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

public class OptionalFieldStorageSteps {

    private static final Map<String, String> COLUMNS = Map.of(
            "personal_data.middle_name", "middle_name",
            "personal_data.relation_type", "relation_type",
            "personal_data.address", "address",
            "personal_data.document.series", "document_series",
            "personal_data.document.issued_by", "document_issued_by",
            "personal_data.document.issued_at", "document_issued_at",
            "personal_data.document.division_code", "document_division_code");
    private static final String PERSON = """
            select middle_name, relation_type, address, document_series, document_issued_by,
                   document_issued_at::text as document_issued_at, document_division_code
              from contract_persons person
              join lm_policies policy on policy.id = person.lm_policy_id
             where policy.individual_request_id = :request and person.participant_id = :participant
             order by person.created_at desc
             limit 1
            """;
    private static final String COMMENT = """
            select cancel_comment from contracts
             where individual_request_id = :request and insured_participant_id = :participant
            union all
            select cancel_comment from lm_policies
             where individual_request_id = :request and participant_id = :participant
            """;
    private static final String USK_NUMBER = """
            select policy_usk_number from lm_policies
             where individual_request_id = :request and participant_id = :participant
            """;

    private final JdbcClient jdbc;

    public OptionalFieldStorageSteps(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Тогда("в снимке сторон участника {string} заявки {string} поле {string} пусто")
    public void fieldIsEmpty(String participantId, String requestId, String path) {
        assertThat(personValue(participantId, requestId, path)).isNull();
    }

    @Тогда("в снимке сторон участника {string} заявки {string} поле {string} сохранено как {string}")
    public void fieldIsStored(String participantId, String requestId, String path, String value) {
        assertThat(personValue(participantId, requestId, path)).isEqualTo(OptionalNodeKinds.text(value));
    }

    @Тогда("комментарий прекращения участника {string} заявки {string} сохранён как {string}")
    public void commentIsStored(String participantId, String requestId, String value) {
        assertThat(single(COMMENT, participantId, requestId, "cancel_comment"))
                .isEqualTo(OptionalNodeKinds.text(value));
    }

    @Тогда("номер в 1С полиса ЛМ участника {string} заявки {string} сохранён как {string}")
    public void uskNumberIsStored(String participantId, String requestId, String value) {
        assertThat(single(USK_NUMBER, participantId, requestId, "policy_usk_number"))
                .isEqualTo(OptionalNodeKinds.text(value));
    }

    private @Nullable String personValue(String participantId, String requestId, String path) {
        String column = COLUMNS.get(tail(path));
        if (column == null) {
            throw new IllegalArgumentException("неизвестное поле: " + path);
        }
        return single(PERSON, participantId, requestId, column);
    }

    private @Nullable String single(String sql, String participantId, String requestId, String column) {
        return jdbc.sql(sql).param("request", UUID.fromString(requestId))
                .param("participant", UUID.fromString(participantId))
                .query((row, index) -> new Stored(text(row, column))).single().text();
    }

    private record Stored(@Nullable String text) {
    }

    private static @Nullable String text(ResultSet row, String column) throws SQLException {
        return row.getString(column);
    }

    private static String tail(String path) {
        int bracket = path.indexOf(']');
        if (bracket < 0 || !path.startsWith("participants[")) {
            throw new IllegalArgumentException("неизвестное поле: " + path);
        }
        return path.substring(bracket + 2);
    }
}
