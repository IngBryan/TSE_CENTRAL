package tse.lab.trackinggenerator;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Command line options, as --name value pairs. */
final class Options {

    private final Map<String, String> values;
    private final Set<String> used = new HashSet<>();

    private Options(Map<String, String> values) {
        this.values = values;
    }

    static Options parse(String[] args, int from) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = from; i < args.length; i += 2) {
            String name = args[i];
            if (!name.startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --name value, got: " + name);
            }
            values.put(name.substring(2), args[i + 1]);
        }
        return new Options(values);
    }

    /** @param defaultValue null if the option is required */
    String string(String name, String defaultValue) {
        used.add(name);
        String value = values.getOrDefault(name, defaultValue);
        if (value == null) {
            throw new IllegalArgumentException("Missing --" + name);
        }
        return value;
    }

    int integer(String name, Integer defaultValue) {
        return Integer.parseInt(string(name, defaultValue == null ? null : defaultValue.toString()));
    }

    long longValue(String name, Long defaultValue) {
        return Long.parseLong(string(name, defaultValue == null ? null : defaultValue.toString()));
    }

    double decimal(String name, Double defaultValue) {
        return Double.parseDouble(string(name, defaultValue == null ? null : defaultValue.toString()));
    }

    /** Accepts 90s, 5m, 1h or a plain number of seconds. */
    Duration duration(String name, String defaultValue) {
        return parseDuration(string(name, defaultValue));
    }

    static Duration parseDuration(String text) {
        String value = text.strip().toLowerCase();
        char unit = value.charAt(value.length() - 1);
        if (Character.isDigit(unit)) {
            return Duration.ofMillis(Math.round(Double.parseDouble(value) * 1000));
        }
        double amount = Double.parseDouble(value.substring(0, value.length() - 1));
        long seconds = switch (unit) {
            case 's' -> 1;
            case 'm' -> 60;
            case 'h' -> 3600;
            default -> throw new IllegalArgumentException("Unknown duration unit in: " + text);
        };
        return Duration.ofMillis(Math.round(amount * seconds * 1000));
    }

    /** Fails on options nobody read, which are usually typos. */
    void rejectUnknown() {
        Set<String> unknown = new HashSet<>(values.keySet());
        unknown.removeAll(used);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown options: --" + String.join(", --", unknown));
        }
    }
}
