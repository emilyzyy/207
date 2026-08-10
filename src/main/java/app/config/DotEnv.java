package app.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal {@code .env} loader. Values do not override existing system properties or env vars.
 */
public final class DotEnv {
    private static final Map<String, String> VALUES = new HashMap<String, String>();
    private static boolean loaded;

    private DotEnv() {

    }
    /** Loads {@code .env} from the process working directory if present. */

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loadFile(Paths.get(".env"));
        loaded = true;
    }

    /**
     * Performs the l oa df il e operation.
     * @param path the p at h value
     */
    public static synchronized void loadFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                final int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                final String key = trimmed.substring(0, eq).trim();
                final String value = unquote(trimmed.substring(eq + 1).trim());
                if (!key.isEmpty() && !VALUES.containsKey(key)) {
                    VALUES.put(key, value);
                }
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to read .env: " + exception.getMessage(), exception);
        }
    }

    /**
     * Resolution order: {@code -D} system property, process env, then {@code .env}.
      * @param systemPropertyName the s ys te mp ro pe rt yn am e value
      * @param envName the e nv na me value
      * @return the result of the operation
     */
    public static String get(String envName, String systemPropertyName) {
        load();
        final String fromProp = systemPropertyName == null ? null : System.getProperty(systemPropertyName);
        if (notBlank(fromProp)) {
            return fromProp.trim();
        }
        final String fromEnv = System.getenv(envName);
        if (notBlank(fromEnv)) {
            return fromEnv.trim();
        }
        final String fromFile = VALUES.get(envName);
        return notBlank(fromFile) ? fromFile.trim() : null;
    }

    /**
     * Performs the s na ps ho t operation.
     * @return the result of the operation
     */
    public static Map<String, String> snapshot() {
        load();
        return Collections.unmodifiableMap(new HashMap<String, String>(VALUES));
    }

    /**
     * True when both Supabase credentials resolve from a -D property, env var, or .env.
     * @return the result of the operation
     */
    public static boolean supabaseConfigured() {
        return get("TRIPPY_SUPABASE_URL", "trippy.supabase.url") != null
                && get("TRIPPY_SUPABASE_ANON_KEY", "trippy.supabase.anonKey") != null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            final char first = value.charAt(0);
            final char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
