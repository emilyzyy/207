package closeai.adapters.controllers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonRequest {
    private final String body;
    public JsonRequest(String body) { this.body = body == null ? "" : body; }
    public String get(String key, String fallback) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}
