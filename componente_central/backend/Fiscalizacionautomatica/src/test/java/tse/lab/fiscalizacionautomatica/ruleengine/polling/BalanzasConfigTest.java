package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BalanzasConfigTest {

    @TempDir
    Path dir;

    @Test
    void inDevItUsesTheEnvironmentAndLocalhostByDefault() throws IOException {
        assertEquals(new BalanzasConfig("http://localhost:3001", "componente-central"),
                BalanzasConfig.load(Map.of("BALANZAS_CLIENT_ID", "componente-central")));
        assertEquals(new BalanzasConfig("http://host.docker.internal:3001", "componente-central"),
                BalanzasConfig.load(Map.of("ENVIRONMENT", "dev", "BALANZAS_URL", "http://host.docker.internal:3001",
                        "BALANZAS_CLIENT_ID", "componente-central")));
    }

    @Test
    void inDevTheClientIdIsRequired() {
        assertThrows(IOException.class, () -> BalanzasConfig.load(Map.of()));
    }

    @Test
    void outsideDevItReadsTheConfigurationFile() throws IOException {
        Path file = dir.resolve("app.json");
        Files.writeString(file, """
                {"balanzasUrl": "https://balanzas.example.uy/", "balanzasClientId": "componente-central"}
                """);

        assertEquals(new BalanzasConfig("https://balanzas.example.uy", "componente-central"),
                BalanzasConfig.load(Map.of("ENVIRONMENT", "prod", "CONFIG_FILE", file.toString(),
                        // environment variables are ignored outside dev
                        "BALANZAS_URL", "http://localhost:3001")));
    }

    @Test
    void outsideDevAMissingFileOrKeyFails() throws IOException {
        Path file = dir.resolve("app.json");
        Files.writeString(file, "{\"balanzasUrl\": \"https://balanzas.example.uy\"}");

        assertThrows(IOException.class, () -> BalanzasConfig.load(Map.of("ENVIRONMENT", "prod", "CONFIG_FILE",
                dir.resolve("missing.json").toString())));
        assertThrows(IOException.class,
                () -> BalanzasConfig.load(Map.of("ENVIRONMENT", "prod", "CONFIG_FILE", file.toString())));
    }
}
