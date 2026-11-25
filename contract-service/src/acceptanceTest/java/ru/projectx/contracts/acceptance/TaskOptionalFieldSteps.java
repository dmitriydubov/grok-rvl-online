package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Когда;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class TaskOptionalFieldSteps {

    private static final List<Map<String, String>> THREE = List.of(
            Map.of("участник", "ae8312c3-350b-4d0b-806c-5e2460e74dbc", "родственник", "нет", "доплата", "0.00"),
            Map.of("участник", "9275471c-d2fe-484f-97a6-d6d7b1267b5e", "родственник", "да", "доплата", "34000.00",
                    "родство", "SPOUSE"),
            Map.of("участник", "6778e8c2-985a-4d54-9bd3-0f6403b104d6", "родственник", "да", "доплата", "0.00",
                    "родство", "CHILD"));
    private static final List<Map<String, String>> INSURED_WITH_COPAY = List.of(
            Map.of("участник", "ae8312c3-350b-4d0b-806c-5e2460e74dbc", "родственник", "нет", "доплата", "12500.00"));

    private final TaskMessages messages;
    private final TaskQueue queue;
    private final PublishedMessages published;
    private final JsonMapper json;

    public TaskOptionalFieldSteps(TaskMessages messages, TaskQueue queue, PublishedMessages published, JsonMapper json) {
        this.messages = messages;
        this.queue = queue;
        this.published = published;
        this.json = json;
    }

    @Когда("сервис Заявок публикует в свою очередь задачу на выпуск {string} по заявке {string} версии {int} с нестроковым узлом {string} в поле {string}")
    public void nonTextField(String messageId, String requestId, int version, String defect, String path) {
        ObjectNode body = task(messageId, requestId, version, THREE);
        OptionalNodeKinds.set(data(body), path, OptionalNodeKinds.node(json, defect));
        publish(messageId, body);
    }

    @Когда("сервис Заявок публикует в свою очередь задачу на выпуск {string} по заявке {string} версии {int} с отсутствием {string} в поле {string}")
    public void absentField(String messageId, String requestId, int version, String absence, String path) {
        ObjectNode body = task(messageId, requestId, version, THREE);
        OptionalNodeKinds.clear(data(body), path, absence);
        publish(messageId, body);
    }

    @Когда("сервис Заявок публикует в свою очередь задачу на выпуск {string} по заявке {string} версии {int} по застрахованному с доплатой 12500.00 и отсутствием {string} в программе")
    public void absentProgramOfInsured(String messageId, String requestId, int version, String absence) {
        ObjectNode body = task(messageId, requestId, version, INSURED_WITH_COPAY);
        OptionalNodeKinds.clear(data(body), "participants[0].program_id", absence);
        publish(messageId, body);
    }

    @Когда("сервис Заявок публикует в свою очередь задачу на выпуск {string} по заявке {string} версии {int} со строкой {string} в поле {string}")
    public void textField(String messageId, String requestId, int version, String value, String path) {
        ObjectNode body = task(messageId, requestId, version, THREE);
        OptionalNodeKinds.set(data(body), path, json.getNodeFactory().stringNode(OptionalNodeKinds.text(value)));
        publish(messageId, body);
    }

    private ObjectNode task(String messageId, String requestId, int version, List<Map<String, String>> participants) {
        return messages.upserted(messageId, requestId, version, participants);
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
