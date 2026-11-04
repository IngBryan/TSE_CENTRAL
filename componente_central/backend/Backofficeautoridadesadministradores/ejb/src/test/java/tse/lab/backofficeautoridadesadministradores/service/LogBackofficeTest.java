package tse.lab.backofficeautoridadesadministradores.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LogBackofficeTest {

    @Test
    void fijaElComponenteDelBackoffice() {
        String log = LogBackoffice.evento("nodo.creado").campo("nodo", 9001).toString();

        assertTrue(log.contains("\"componente\":\"backoffice\",\"evento\":\"nodo.creado\""), log);
        assertTrue(log.contains("\"datos\":{\"nodo\":9001}"), log);
    }
}
