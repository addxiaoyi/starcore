package dev.starcore.starcore.api.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * API 请求数据验证器
 * 提供统一的请求参数验证逻辑
 */
public final class ApiValidator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static final Pattern NAME_PATTERN = Pattern.compile(
        "^[\\u4e00-\\u9fa5a-zA-Z0-9_]{2,32}$"
    );

    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 32;

    private ApiValidator() {
    }

    /**
     * 验证必填字段
     */
    public static List<String> validateRequired(Object value, String fieldName) {
        List<String> errors = new ArrayList<>();
        if (value == null) {
            errors.add(fieldName + " is required");
        } else if (value instanceof String str && str.isBlank()) {
            errors.add(fieldName + " cannot be empty");
        }
        return errors;
    }

    /**
     * 验证 UUID 格式
     */
    public static List<String> validateUuid(String value, String fieldName) {
        List<String> errors = new ArrayList<>();
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " is required");
        } else if (!UUID_PATTERN.matcher(value).matches()) {
            errors.add(fieldName + " must be a valid UUID");
        }
        return errors;
    }

    /**
     * 验证名称格式
     */
    public static List<String> validateName(String value, String fieldName) {
        List<String> errors = new ArrayList<>();
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " is required");
        } else {
            if (value.length() < MIN_NAME_LENGTH) {
                errors.add(fieldName + " must be at least " + MIN_NAME_LENGTH + " characters");
            }
            if (value.length() > MAX_NAME_LENGTH) {
                errors.add(fieldName + " must be at most " + MAX_NAME_LENGTH + " characters");
            }
            if (!NAME_PATTERN.matcher(value).matches()) {
                errors.add(fieldName + " can only contain Chinese, English, numbers and underscores");
            }
        }
        return errors;
    }

    /**
     * 验证数值范围
     */
    public static List<String> validateRange(int value, int min, int max, String fieldName) {
        List<String> errors = new ArrayList<>();
        if (value < min) {
            errors.add(fieldName + " must be at least " + min);
        }
        if (value > max) {
            errors.add(fieldName + " must be at most " + max);
        }
        return errors;
    }

    /**
     * 验证正数
     */
    public static List<String> validatePositive(double value, String fieldName) {
        List<String> errors = new ArrayList<>();
        if (value <= 0) {
            errors.add(fieldName + " must be positive");
        }
        return errors;
    }

    /**
     * 验证非负数
     */
    public static List<String> validateNonNegative(double value, String fieldName) {
        List<String> errors = new ArrayList<>();
        if (value < 0) {
            errors.add(fieldName + " must be non-negative");
        }
        return errors;
    }

    /**
     * 验证页码
     */
    public static List<String> validatePage(int page) {
        List<String> errors = new ArrayList<>();
        if (page < 1) {
            errors.add("page must be at least 1");
        }
        return errors;
    }

    /**
     * 验证分页大小
     */
    public static List<String> validatePageSize(int pageSize) {
        List<String> errors = new ArrayList<>();
        if (pageSize < 1) {
            errors.add("pageSize must be at least 1");
        }
        if (pageSize > 100) {
            errors.add("pageSize must be at most 100");
        }
        return errors;
    }

    /**
     * 验证世界名称
     */
    public static List<String> validateWorldName(String value, String fieldName) {
        List<String> errors = new ArrayList<>();
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " is required");
        } else {
            if (value.length() > 64) {
                errors.add(fieldName + " is too long (max 64 characters)");
            }
            if (!value.matches("^[a-zA-Z0-9_.-]+$")) {
                errors.add(fieldName + " contains invalid characters");
            }
        }
        return errors;
    }

    /**
     * 合并多个验证结果
     */
    @SafeVarargs
    public static List<String> merge(List<String>... errorLists) {
        List<String> result = new ArrayList<>();
        for (List<String> errors : errorLists) {
            if (errors != null) {
                result.addAll(errors);
            }
        }
        return result;
    }

    /**
     * 解析 UUID（返回 null 如果无效）
     */
    public static UUID parseUuid(String value) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 解析整数（返回 -1 如果无效）
     */
    public static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析长整数（返回 -1 如果无效）
     */
    public static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
