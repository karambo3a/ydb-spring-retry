package tech.ydb.slo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SloConfig(
        String connectionString,
        String otlpEndpoint,
        int reportPeriod,
        int readRps,
        int readTimeout,
        int writeRps,
        int writeTimeout,
        int time
) {

    record ParsedArgs(String command, String connectionString, Map<String, String> options, String[] springArgs) {
    }

    public String jdbcUrl() {
        return "jdbc:ydb:" + connectionString;
    }

    public static SloConfig parse(String connectionString, Map<String, String> options) {
        return new SloConfig(
                connectionString,
                requireNonEmpty(options, "otlp-endpoint"),
                optionalInt(options, "report-period", 250),
                requireInt(options, "read-rps"),
                optionalInt(options, "read-timeout", 10000),
                requireInt(options, "write-rps"),
                optionalInt(options, "write-timeout", 10000),
                requireInt(options, "time")
        );
    }

    static ParsedArgs parseArgs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("No arguments provided");
        }

        String command = args[0];
        List<String> positional = new ArrayList<>();
        Map<String, String> options = new HashMap<>();
        List<String> springArgs = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--spring.") || arg.startsWith("--ydb.") || arg.startsWith("-D")) {
                springArgs.add(arg);
            } else if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 0) {
                    options.put(arg.substring(2, eq), arg.substring(eq + 1));
                } else {
                    String key = arg.substring(2);
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        options.put(key, args[++i]);
                    } else {
                        options.put(key, "true");
                    }
                }
            } else {
                positional.add(arg);
            }
        }

        if (positional.size() != 2) {
            throw new IllegalArgumentException(
                    "Expected exactly <endpoint> and <database> as positional arguments, got: " + positional);
        }

        String connectionString = positional.get(0) + positional.get(1);

        return new ParsedArgs(command, connectionString, options, springArgs.toArray(new String[0]));
    }

    private static String requireNonEmpty(Map<String, String> options, String key) {
        String value = options.remove(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required option: --" + key);
        }
        return value;
    }

    private static int requireInt(Map<String, String> options, String key) {
        String raw = requireNonEmpty(options, key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for --" + key + ": " + raw);
        }
    }

    private static int optionalInt(Map<String, String> options, String key, int defaultValue) {
        String raw = options.remove(key);
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for --" + key + ": " + raw);
        }
    }
}
