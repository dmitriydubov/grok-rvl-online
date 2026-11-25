package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Когда;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class CancellationOptionalFieldSteps {

    private static final Map<String, String> FIELDS = Map.of(
            "аннулировано", "2026-02-12T14:30:00+03:00",
            "причина", "INSURED_REFUSED",
            "комментарий", "Стенд: комментарий");

    private final TaskMessages messages;
    private final TaskQueue queue;
    private final PublishedMessages published;
    private final JsonMapper json;

    public CancellationOptionalFieldSteps(TaskMessages messages, TaskQueue queue, PublishedMessages published,
            JsonMapper json) {
        this.messages = messages;
        this.queue = queue;
        this.published = published;
        this.json = json;
    }

    @Когда("сервис Заявок публикует в свою очередь прекращение работы {string} по заявке {string} версии {int} с нестроковым узлом {string} в комментарии")
    public void nonTextComment(String messageId, String requestId, int version, String defect) {
        ObjectNode body = cancelled(messageId, requestId, version);
        data(body).set("cancel_comment", OptionalNodeKinds.node(json, defect));
        publish(messageId, body);
    }

    @Когда("сервис Заявок публикует в свою очередь прекращение работы {string} по заявке {string} версии {int} с отсутствием {string} в комментарии")
    public void absentComment(String messageId, String requestId, int version, String absence) {
        ObjectNode body = cancelled(messageId, requestId, version);
        clear(data(body), absence);
        publish(messageId, body);
    }

    @Когда("сервис Заявок публикует в свою очередь прекращение работы {string} по заявке {string} версии {int} с комментарием {string}")
    public void textComment(String messageId, String requestId, int version, String value) {
        ObjectNode body = cancelled(messageId, requestId, version);
        data(body).put("cancel_comment", OptionalNodeKinds.text(value));
        publish(messageId, body);
    }

    private ObjectNode cancelled(String messageId, String requestId, int version) {
        return messages.cancelled(messageId, requestId, version, FIELDS);
    }

    private static void clear(ObjectNode data, String absence) {
        if ("нет ключа".equals(absence)) {
            data.remove("cancel_comment");
            return;
        }
        if ("null".equals(absence)) {
            data.putNull("cancel_comment");
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
        published.rememberQueued(queue.alias(), messageId);
        queue.publish(bytes);
    }
}
