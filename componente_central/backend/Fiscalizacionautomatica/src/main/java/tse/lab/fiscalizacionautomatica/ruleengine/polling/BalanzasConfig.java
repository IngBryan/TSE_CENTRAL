package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Where the balanzas node is and the OAuth client the poller uses (not secret). With ENVIRONMENT=dev (the
 * default) they come from environment variables, as in a local run or the compose; otherwise from a JSON
 * configuration file (in Kubernetes, a ConfigMap mounted in /app/config/app.json). The client secret is
 * always an environment variable (BALANZAS_CLIENT_SECRET).
 */
public record BalanzasConfig(String url, String clientId) {

    static final String DEFAULT_FILE = "/app/config/app.json";
    static final String DEV_URL = "http://localhost:3001";

    public static BalanzasConfig load() throws IOException {
        return load(System.getenv());
    }

    static BalanzasConfig load(Map<String, String> env) throws IOException {
        if ("dev".equals(value(env, "ENVIRONMENT", "dev"))) {
            String clientId = value(env, "BALANZAS_CLIENT_ID", null);
            if (clientId == null) {
                throw new IOException("Missing environment variable BALANZAS_CLIENT_ID");
            }
            return new BalanzasConfig(value(env, "BALANZAS_URL", DEV_URL), clientId);
        }
        Path file = Path.of(value(env, "CONFIG_FILE", DEFAULT_FILE));
        JsonNode json;
        try {
            json = new ObjectMapper().readTree(Files.readString(file));
        } catch (IOException e) {
            throw new IOException("Could not read the configuration " + file + ": " + e.getMessage(), e);
        }
        return new BalanzasConfig(required(json, "balanzasUrl", file).replaceAll("/$", ""),
                required(json, "balanzasClientId", file));
    }

    private static String value(Map<String, String> env, String name, String defaultValue) {
        String value = env.get(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String required(JsonNode json, String key, Path file) throws IOException {
        JsonNode node = json == null ? null : json.get(key);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IOException("Missing " + key + " in " + file);
        }
        return node.asText();
    }
}
