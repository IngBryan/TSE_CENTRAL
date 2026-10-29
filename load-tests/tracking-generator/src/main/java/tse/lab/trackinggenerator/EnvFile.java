package tse.lab.trackinggenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The .env of the generator (load-tests/tracking-generator/.env, next to target/ where the jar is),
 * as KEY=VALUE lines. Not the .env of the repository root: that one has the local docker-compose
 * credentials.
 */
final class EnvFile {

    private EnvFile() {
    }

    static Map<String, String> load() {
        Path file = location();
        if (file == null || !Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            Map<String, String> values = new HashMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String entry = line.strip();
                int equals = entry.indexOf('=');
                if (entry.isEmpty() || entry.startsWith("#") || equals < 1) {
                    continue;
                }
                values.put(entry.substring(0, equals).strip(), unquote(entry.substring(equals + 1).strip()));
            }
            System.out.println("Read " + file);
            return values;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static Path location() {
        try {
            Path jar = Path.of(EnvFile.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path target = jar.getParent();
            return target == null || target.getParent() == null ? null : target.getParent().resolve(".env");
        } catch (URISyntaxException | SecurityException e) {
            return null;
        }
    }

    private static String unquote(String value) {
        boolean quoted = value.length() >= 2 && (value.startsWith("'") && value.endsWith("'")
                || value.startsWith("\"") && value.endsWith("\""));
        return quoted ? value.substring(1, value.length() - 1) : value;
    }
}
