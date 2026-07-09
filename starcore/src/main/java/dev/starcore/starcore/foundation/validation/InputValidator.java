package dev.starcore.starcore.foundation.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 输入验证器 - SSS级安全
 * 防止SQL注入、XSS、命令注入等安全问题
 */
public final class InputValidator {

    // SQL注入防护
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('.+--)|(--)|(;)|(\\|\\|)|(&&)|(exec(\\s|\\()+)|(execute(\\s|\\()+)|" +
        "(select.+from)|(insert.+into)|(delete.+from)|(update.+set)|(drop.+table)",
        Pattern.CASE_INSENSITIVE
    );

    // XSS防护
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(<script[^>]*>)|(</script>)|(<iframe[^>]*>)|(javascript:)|(onerror=)|(onload=)",
        Pattern.CASE_INSENSITIVE
    );

    // 命令注入防护
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
        "(;)|(\\|\\|)|(&&)|(\\$\\()|(`)|(>)|(>>)|(\\|)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 验证国家名称
     */
    public static ValidationResult validateNationName(String name) {
        List<String> errors = new ArrayList<>();

        if (name == null || name.isBlank()) {
            errors.add("国家名称不能为空");
            return ValidationResult.failure(errors);
        }

        if (name.length() < 2 || name.length() > 20) {
            errors.add("国家名称长度必须在2-20个字符之间");
        }

        if (!name.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
            errors.add("国家名称只能包含字母、数字、下划线和中文");
        }

        if (containsSQLInjection(name)) {
            errors.add("国家名称包含非法字符");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 验证玩家输入的文本
     */
    public static ValidationResult validatePlayerInput(String input, int maxLength) {
        List<String> errors = new ArrayList<>();

        if (input == null) {
            errors.add("输入不能为空");
            return ValidationResult.failure(errors);
        }

        if (input.length() > maxLength) {
            errors.add("输入长度不能超过" + maxLength + "个字符");
        }

        if (containsSQLInjection(input)) {
            errors.add("输入包含非法SQL字符");
        }

        if (containsXSS(input)) {
            errors.add("输入包含非法脚本字符");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 验证数量
     */
    public static ValidationResult validateAmount(int amount, int min, int max) {
        List<String> errors = new ArrayList<>();

        if (amount < min) {
            errors.add("数量不能小于" + min);
        }

        if (amount > max) {
            errors.add("数量不能大于" + max);
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 清理用户输入（移除危险字符）
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }

        return input
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#x27;")
            .replaceAll("/", "&#x2F;");
    }

    /**
     * 检测SQL注入
     */
    public static boolean containsSQLInjection(String input) {
        if (input == null) {
            return false;
        }
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * 检测XSS
     */
    public static boolean containsXSS(String input) {
        if (input == null) {
            return false;
        }
        return XSS_PATTERN.matcher(input).find();
    }

    /**
     * 检测命令注入
     */
    public static boolean containsCommandInjection(String input) {
        if (input == null) {
            return false;
        }
        return COMMAND_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * 验证结果
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
}
