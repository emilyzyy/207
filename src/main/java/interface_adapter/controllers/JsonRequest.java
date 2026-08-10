package interface_adapter.controllers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonRequest {
    private final String body;

    public JsonRequest(String body) {
        this.body = body == null ? "" : body;
    }

    /**
     * Performs the g et operation.
     * @param fallback the f al lb ac k value
     * @param key the k ey value
     * @return the result of the operation
     */
    public String get(String key, String fallback) {
        final Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        final Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}
