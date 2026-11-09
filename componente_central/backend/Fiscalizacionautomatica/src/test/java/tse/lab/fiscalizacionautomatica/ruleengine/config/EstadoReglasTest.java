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
    @Test void historicasSeCacheanSinReemplazarLaVigente() throws Exception {
        var loader=org.mockito.Mockito.mock(EstadoReglas.RevisionLoader.class);
        org.mockito.Mockito.when(loader.load(2)).thenReturn(c(2));
        EstadoReglas estado=new EstadoReglas(loader);
        estado.aplicar(c(5));
        assertSame(estado.revision(2),estado.revision(2));
        org.mockito.Mockito.verify(loader).load(2);
        assertEquals(5,estado.actual().getRevision());
        assertThrows(IllegalArgumentException.class,()->estado.aplicar(null));
        assertThrows(IOException.class,()->estado.revision(3));
        org.mockito.Mockito.when(loader.load(4)).thenReturn(c(1));
        assertThrows(IOException.class,()->estado.revision(4));
        var fija=ReglasSource.fija(c(2));
        assertSame(fija.actual(),fija.revision(2));
        assertThrows(IOException.class,()->fija.revision(3));
    }

}
