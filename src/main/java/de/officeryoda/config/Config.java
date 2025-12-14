package de.officeryoda.config;

public class Config {

    public static String get(String key) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            switch (key) {
                case "WHITELISTED_PLAYERS_FILE":
                    return "data/whitelisted.json";
                case "PENDING_PLAYERS_FILE":
                    return "data/pending.json";
                default:
                    throw new RuntimeException("Missing mandatory environment variable: " + key);
            }
        }
        return value;
    }

    public static long getAsLong(String key) {
        String value = get(key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                    "Invalid number format for environment variable: " + key + " with value: " + value, e);
        }
    }
}
