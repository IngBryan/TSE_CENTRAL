package tse.lab.fiscalizacionautomatica.ruleengine.config;

import java.io.IOException;
import tse.lab.reglas.ConfiguracionReglas;

public interface ReglasSource {
    ConfiguracionReglas actual() throws IOException;
    ConfiguracionReglas revision(long numero) throws IOException;
    static ReglasSource fija(ConfiguracionReglas c) {
        return new ReglasSource() {
            public ConfiguracionReglas actual() { return c; }
            public ConfiguracionReglas revision(long n) throws IOException {
                if (n != c.getRevision()) throw new IOException("Revisión no disponible: " + n);
                return c;
            }
        };
    }
}
