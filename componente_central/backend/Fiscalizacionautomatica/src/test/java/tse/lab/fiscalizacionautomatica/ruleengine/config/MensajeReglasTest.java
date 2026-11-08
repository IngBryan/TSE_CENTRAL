package tse.lab.fiscalizacionautomatica.ruleengine.config;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MensajeReglasTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    static byte[] mensaje(long revision) {
        return valido(revision).toString().getBytes(StandardCharsets.UTF_8);
    }
    private static ObjectNode valido(long revision) {
        return JSON.createObjectNode().put("schemaVersion",1).put("revision",revision)
                .put("toleranciaCargaPorcentaje",2.25).put("toleranciaCargaKg",500)
                .put("distanciaDesvioMetros",1500).put("ventanaDesvioSegundos",60)
                .put("fechaActualizacion","2026-11-07T00:00:00Z").put("usuario","admin").put("fundamento","ajuste");
    }
    @Test void conservaTodosLosParametrosDelSnapshotPublicado() throws IOException {
        var c=MensajeReglas.leer(mensaje(2));
        assertEquals(2,c.getRevision());
        assertEquals("2.25",c.getToleranciaCargaPorcentaje().toPlainString());
        assertEquals(500,c.getToleranciaCargaKg());
        assertEquals(1500,c.getDistanciaDesvioMetros());
        assertEquals(60,c.getVentanaDesvioSegundos());
        assertEquals("admin",c.getUsuario());assertEquals("ajuste",c.getFundamento());
        assertTrue(c.isPublicada());
    }
    @Test void rechazaBuffersInvalidosYCamposAusentes() {
        assertThrows(IOException.class,()->MensajeReglas.leer(null));
        assertThrows(IOException.class,()->MensajeReglas.leer(new byte[65537]));
        for (String campo : List.of("revision","toleranciaCargaPorcentaje","ventanaDesvioSegundos","usuario")) {
            var n=valido(2);n.remove(campo);
            assertThrows(IOException.class,()->MensajeReglas.leer(n.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }
    @Test void rechazaTiposIncorrectosYEnterosFueraDeRango() throws IOException {
        for (String cambio : List.of("\"schemaVersion\":2","\"schemaVersion\":1.5","\"revision\":1.5",
                "\"revision\":9223372036854775808","\"toleranciaCargaPorcentaje\":\"2\"",
                "\"toleranciaCargaKg\":1.5","\"toleranciaCargaKg\":2147483648","\"usuario\":5")) {
            var n=valido(2);var campo=JSON.readTree("{"+cambio+"}");n.setAll((ObjectNode)campo);
            assertThrows(IOException.class,()->MensajeReglas.leer(n.toString().getBytes(StandardCharsets.UTF_8)),cambio);
        }
    }
}
