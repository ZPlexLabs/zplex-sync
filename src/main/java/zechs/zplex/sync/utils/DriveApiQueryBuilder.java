package zechs.zplex.sync.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class DriveApiQueryBuilder {

    private final List<String> conditions = new ArrayList<>();

    public DriveApiQueryBuilder nameEquals(String name) {
        addCondition("name", name);
        return this;
    }

    public DriveApiQueryBuilder mimeTypeEquals(String mimeType) {
        addCondition("mimeType =", mimeType);
        return this;
    }

    public DriveApiQueryBuilder mimeTypeNotEquals(String mimeType) {
        addCondition("mimeType !=", mimeType);
        return this;
    }

    public DriveApiQueryBuilder inParents(String parentId) {
        addCondition("'" + parentId + "' in parents");
        return this;
    }

    public DriveApiQueryBuilder trashed(boolean isTrashed) {
        addCondition("trashed", isTrashed);
        return this;
    }

    public String build() {
        StringJoiner joiner = new StringJoiner(" and ");
        for (String condition : conditions) {
            joiner.add(condition);
        }
        return joiner.toString();
    }

    private void addCondition(String field, Object value) {
        String condition = field;
        if (value != null) {
            String operator = field.endsWith("=") || field.endsWith("!=") ? " " : "= ";
            condition += operator + formatValue(value);
        }
        conditions.add(condition);
    }

    private void addCondition(String condition) {
        conditions.add(condition);
    }

    private String formatValue(Object value) {
        if (value instanceof String) {
            return "'" + value + "'";
        }
        return value.toString();
    }
}