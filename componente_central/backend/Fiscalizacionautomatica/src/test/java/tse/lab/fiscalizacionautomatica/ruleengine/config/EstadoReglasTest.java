package tse.lab.fiscalizacionautomatica.ruleengine.config;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import tse.lab.reglas.ConfiguracionReglas;

class EstadoReglasTest {
    static ConfiguracionReglas c(long r) {
        return new ConfiguracionReglas(r,BigDecimal.valueOf(r),0,2000,30,"2026-11-07T00:00:00Z","admin","x",true);
    }
    @Test void bootstrapAntiguoYDuplicadosNoRetrocedenElBroadcast() throws Exception {
        EstadoReglas estado=new EstadoReglas(EstadoReglasTest::c);
        assertThrows(IOException.class,estado::actual);
        assertTrue(estado.aplicar(c(5)));
        assertFalse(estado.aplicar(c(1)));
        assertFalse(estado.aplicar(c(5)));
        assertEquals(5,estado.actual().getRevision());
        assertEquals(2,estado.revision(2).getRevision());
        assertEquals(5,estado.actual().getRevision());
    }
    @Test void cambiosConcurrentesConvergenALaMayorRevision() throws Exception {
        EstadoReglas estado=new EstadoReglas(EstadoReglasTest::c);
        try (var executor=Executors.newFixedThreadPool(4)) {
            for (long i=1;i<=20;i++) { long r=i; executor.submit(()->estado.aplicar(c(r))); }
        }
        assertEquals(20,estado.actual().getRevision());
    }
    @Test void rechazaMensajesMalformadosSinConvertirCamposAusentesEnCero() {
        for (String s : new String[]{"{}","null","[]","{\"schemaVersion\":2}","{\"schemaVersion\":1.5}"})
            assertThrows(IOException.class,()->MensajeReglas.leer(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
