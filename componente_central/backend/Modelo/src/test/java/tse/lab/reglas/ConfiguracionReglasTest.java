package tse.lab.reglas;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionReglasTest {
    private ConfiguracionReglas c(String p, int kg) {
        return new ConfiguracionReglas(2,new BigDecimal(p),kg,2000,30,"2026-11-07T00:00:00Z","admin","prueba",false);
    }
    @Test void margenesAlternativosNoSeSumanYConservanPrecision() {
        assertEquals(0,new BigDecimal("30600").compareTo(c("2",500).limiteCargaKg(30000)));
        assertEquals(0,new BigDecimal("31000").compareTo(c("2",1000).limiteCargaKg(30000)));
        assertEquals(0,new BigDecimal("30003").compareTo(c("0.01",0).limiteCargaKg(30000)));
        assertEquals(0,new BigDecimal("30000").compareTo(c("0",0).limiteCargaKg(30000)));
    }
    @Test void rechazaToleranciasInvalidas() {
        for (String p : new String[]{"-1","20.01","0.001"}) assertThrows(IllegalArgumentException.class,()->c(p,0));
        assertThrows(IllegalArgumentException.class,()->c("0",5001));
        assertThrows(IllegalArgumentException.class,()->new ConfiguracionReglas(2,BigDecimal.ZERO,0,0,30,
                "2026-11-07T00:00:00Z","admin","x",false));
    }
}
