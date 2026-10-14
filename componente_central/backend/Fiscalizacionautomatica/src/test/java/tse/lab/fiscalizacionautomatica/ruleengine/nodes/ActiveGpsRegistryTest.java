package tse.lab.fiscalizacionautomatica.ruleengine.nodes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActiveGpsRegistryTest {

    private Set<Long> wildFlyAnswer = Set.of(1L, 2L);
    private boolean wildFlyDown;

    private final ActiveGpsRegistry registry = new ActiveGpsRegistry(() -> {
        if (wildFlyDown) {
            throw new IOException("WildFly down");
        }
        return wildFlyAnswer;
    });

    @Test
    void knowsTheActiveGpsAfterLoading() throws IOException {
        assertFalse(registry.isActive(1));

        registry.load();

        assertTrue(registry.isActive(1));
        assertTrue(registry.isActive(2));
        assertFalse(registry.isActive(3));
    }

    @Test
    void firstLoadFailsIfWildFlyIsDown() {
        wildFlyDown = true;

        assertThrows(IOException.class, registry::load);
    }

    @Test
    void refreshPicksUpChanges() throws IOException {
        registry.load();
        wildFlyAnswer = Set.of(2L, 3L);

        registry.refresh();

        assertFalse(registry.isActive(1));
        assertTrue(registry.isActive(3));
    }

    @Test
    void failedRefreshKeepsTheLastList() throws IOException {
        registry.load();
        wildFlyDown = true;

        registry.refresh();

        assertTrue(registry.isActive(1));
    }
}
