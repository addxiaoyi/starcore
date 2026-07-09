package dev.starcore.starcore.foundation.message;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageFormatter {
    private static final Pattern NAMED_PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_-]*)\\}");

    private MessageFormatter() {
    }

    public static String format(String pattern, Object... args) {
        if (pattern == null || args == null || args.length == 0) {
            return pattern;
        }

        String formatted = pattern;
        for (int index = 0; index < args.length; index++) {
            formatted = formatted.replace("{" + index + "}", String.valueOf(args[index]));
        }

        List<String> namedPlaceholders = namedPlaceholders(formatted);
        if (usesNamedPairs(args, namedPlaceholders)) {
            for (int index = 0; index + 1 < args.length; index += 2) {
                formatted = formatted.replace("{" + args[index] + "}", String.valueOf(args[index + 1]));
            }
            return formatted;
        }

        int argIndex = 0;
        for (String placeholder : namedPlaceholders) {
            if ("prefix".equals(placeholder)) {
                continue;
            }
            if (argIndex >= args.length) {
                break;
            }
            formatted = formatted.replace("{" + placeholder + "}", String.valueOf(args[argIndex++]));
        }
        return formatted;
    }

    private static boolean usesNamedPairs(Object[] args, List<String> namedPlaceholders) {
        if (args.length < 2 || args.length % 2 != 0) {
            return false;
        }
        for (int index = 0; index < args.length; index += 2) {
            if (!(args[index] instanceof CharSequence)) {
                return false;
            }
            if (!namedPlaceholders.contains(String.valueOf(args[index]))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> namedPlaceholders(String pattern) {
        List<String> placeholders = new ArrayList<>();
        Matcher matcher = NAMED_PLACEHOLDER.matcher(pattern);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!placeholders.contains(placeholder)) {
                placeholders.add(placeholder);
            }
        }
        return placeholders;
    }
}
