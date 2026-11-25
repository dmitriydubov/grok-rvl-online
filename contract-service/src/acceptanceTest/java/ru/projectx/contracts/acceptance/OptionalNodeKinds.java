package ru.projectx.contracts.acceptance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class OptionalNodeKinds {

    private static final Map<String, Function<JsonMapper, JsonNode>> NODES = Map.of(
            "число 0", json -> json.getNodeFactory().numberNode(0),
            "число 12.34", json -> json.getNodeFactory().numberNode(new BigDecimal("12.34")),
            "логическое true", json -> json.getNodeFactory().booleanNode(true),
            "логическое false", json -> json.getNodeFactory().booleanNode(false),
            "пустой массив", JsonMapper::createArrayNode,
            "массив из строки «вымышленная»", json -> json.createArrayNode().add("вымышленная"),
            "пустой объект", JsonMapper::createObjectNode,
            "объект с полем «метка»", json -> json.createObjectNode().put("метка", "вымышленная"));
    private static final Set<String> INDEXES = Set.of("0", "1", "2");
    private static final Set<String> TAILS = Set.of("program_id", "personal_data.middle_name",
            "personal_data.relation_type", "personal_data.address", "personal_data.document.series",
            "personal_data.document.issued_by", "personal_data.document.issued_at",
            "personal_data.document.division_code");

    private OptionalNodeKinds() {
    }

    static JsonNode node(JsonMapper json, String defect) {
        Function<JsonMapper, JsonNode> kind = NODES.get(defect);
        if (kind == null) {
            throw new IllegalArgumentException("неизвестный дефект: " + defect);
        }
        return kind.apply(json);
    }

    static String text(String value) {
        return switch (value) {
            case "пустая строка" -> "";
            case "строка из пробелов" -> "   ";
            default -> throw new IllegalArgumentException("неизвестное значение: " + value);
        };
    }

    static void set(ObjectNode data, String path, JsonNode value) {
        if (!known(path)) {
            throw new IllegalArgumentException("неизвестное поле: " + path);
        }
        parent(data, path).set(leaf(path), value);
    }

    static void clear(ObjectNode data, String path, String absence) {
        if (!known(path)) {
            throw new IllegalArgumentException("неизвестное поле: " + path);
        }
        ObjectNode parent = parent(data, path);
        switch (absence) {
            case "нет ключа" -> parent.remove(leaf(path));
            case "null" -> parent.putNull(leaf(path));
            default -> throw new IllegalArgumentException("неизвестное отсутствие: " + absence);
        }
    }

    private static boolean known(String path) {
        if (!indexed(path)) {
            return false;
        }
        int bracket = path.indexOf(']');
        return INDEXES.contains(path.substring("participants[".length(), bracket))
                && TAILS.contains(path.substring(bracket + 2));
    }

    private static boolean indexed(String path) {
        int bracket = path.indexOf(']');
        return bracket > 0 && path.startsWith("participants[") && path.startsWith("].", bracket);
    }

    private static ObjectNode parent(ObjectNode data, String path) {
        String[] parts = path.split("\\.");
        ObjectNode current = data;
        for (int i = 0; i < parts.length - 1; i++) {
            current = objectChild(current, parts[i]);
        }
        return current;
    }

    private static ObjectNode objectChild(ObjectNode node, String segment) {
        JsonNode child = node.get(name(segment));
        if (segment.endsWith("]")) {
            child = child.get(index(segment));
        }
        if (!(child instanceof ObjectNode object)) {
            throw new IllegalArgumentException("неизвестное поле: " + segment);
        }
        return object;
    }

    private static String name(String segment) {
        int bracket = segment.indexOf('[');
        return bracket < 0 ? segment : segment.substring(0, bracket);
    }

    private static int index(String segment) {
        return Integer.parseInt(segment.substring(segment.indexOf('[') + 1, segment.indexOf(']')));
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
