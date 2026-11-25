package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Когда;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class LmPolicyOptionalFieldSteps {

    private static final String RESULTS = "результаты выпуска полисов ЛМ";
    private static final String NUMBER = "policy_usk_number";
    private static final Map<String, String> FAILED = Map.of(
            "status", "ERROR",
            "error_code", "INSURED_NOT_COVERED",
            "error_message", "Стенд: застрахованный не входит в корпоративный договор");

    private final OneCMessages messages;
    private final StandQueues queues;
    private final PublishedMessages published;
    private final IssuanceRecords records;
    private final JsonMapper json;

    public LmPolicyOptionalFieldSteps(OneCMessages messages, StandQueues queues, PublishedMessages published,
            IssuanceRecords records, JsonMapper json) {
        this.messages = messages;
        this.queues = queues;
        this.published = published;
        this.records = records;
        this.json = json;
    }

    @Когда("1С:УСК публикует в свою очередь результат выпуска {string} по полису ЛМ участника {string} заявки {string} со статусом ISSUED и нестроковым узлом {string} в номере полиса")
    public void nonTextNumber(String messageId, String participantId, String requestId, String defect) {
        publish(messageId, issued(messageId, participantId, requestId, OptionalNodeKinds.node(json, defect)));
    }

    @Когда("1С:УСК публикует в свою очередь результат выпуска {string} по полису ЛМ участника {string} заявки {string} со статусом ERROR и нестроковым узлом {string} в номере полиса")
    public void nonTextNumberWhenFailed(String messageId, String participantId, String requestId, String defect) {
        ObjectNode body = messages.issued(messageId, requestId, records.latestLmPolicyId(participantId, requestId),
                FAILED);
        data(body).set(NUMBER, OptionalNodeKinds.node(json, defect));
        publish(messageId, body);
    }

    @Когда("1С:УСК публикует в свою очередь результат выпуска {string} по полису ЛМ участника {string} заявки {string} со статусом ISSUED и отсутствием {string} в номере полиса")
    public void absentNumber(String messageId, String participantId, String requestId, String absence) {
        ObjectNode body = issued(messageId, participantId, requestId, json.getNodeFactory().stringNode("LM-000012204"));
        clear(data(body), absence);
        publish(messageId, body);
    }

    @Когда("1С:УСК публикует в свою очередь результат выпуска {string} по полису ЛМ участника {string} заявки {string} со статусом ISSUED и строкой {string} в номере полиса")
    public void textNumber(String messageId, String participantId, String requestId, String value) {
        publish(messageId, issued(messageId, participantId, requestId,
                json.getNodeFactory().stringNode(OptionalNodeKinds.text(value))));
    }

    private ObjectNode issued(String messageId, String participantId, String requestId, JsonNode number) {
        ObjectNode body = messages.issued(messageId, requestId, records.latestLmPolicyId(participantId, requestId),
                OneCMessages.ISSUED_EXAMPLE);
        data(body).set(NUMBER, number);
        return body;
    }

    private static void clear(ObjectNode data, String absence) {
        if ("нет ключа".equals(absence)) {
            data.remove(NUMBER);
            return;
        }
        if ("null".equals(absence)) {
            data.putNull(NUMBER);
            return;
        }
        throw new IllegalArgumentException("неизвестное отсутствие: " + absence);
    }

    private static ObjectNode data(ObjectNode body) {
        return (ObjectNode) body.get("data");
    }

    private void publish(String messageId, ObjectNode body) {
        byte[] bytes = messages.bytes(body);
        published.remember(messageId, bytes);
        published.rememberQueued(RESULTS, messageId);
        ((OneCQueue) queues.called(RESULTS)).publish(bytes);
    }
}
