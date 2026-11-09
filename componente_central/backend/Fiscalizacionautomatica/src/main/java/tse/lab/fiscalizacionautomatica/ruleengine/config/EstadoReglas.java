package tse.lab.fiscalizacionautomatica.ruleengine.config;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import tse.lab.reglas.ConfiguracionReglas;

/** Una sustitución atómica por snapshot; ni mensajes viejos ni bootstrap pueden retrocederla. */
public class EstadoReglas implements ReglasSource {
    @FunctionalInterface public interface RevisionLoader { ConfiguracionReglas load(long n) throws IOException; }
    private final AtomicReference<ConfiguracionReglas> actual=new AtomicReference<>();
    private final ConcurrentHashMap<Long, ConfiguracionReglas> revisiones=new ConcurrentHashMap<>();
    private final RevisionLoader loader;
    public EstadoReglas(RevisionLoader loader) { this.loader=loader; }
    public boolean aplicar(ConfiguracionReglas c) {
        if (c == null) throw new IllegalArgumentException("Falta configuración");
        revisiones.putIfAbsent(c.getRevision(), c);
        while (true) {
            ConfiguracionReglas previa=actual.get();
            if (previa!=null && previa.getRevision()>=c.getRevision()) return false;
            if (actual.compareAndSet(previa, c)) return true;
        }
    }
    public ConfiguracionReglas actual() throws IOException {
        ConfiguracionReglas c=actual.get();
        if (c==null) throw new IOException("Esperando la configuración inicial de reglas");
        return c;
    }
    public ConfiguracionReglas revision(long n) throws IOException {
        ConfiguracionReglas c=revisiones.get(n);
        if (c!=null) return c;
        c=loader.load(n);
        if (c==null || c.getRevision()!=n) throw new IOException("Respuesta de revisión incorrecta");
        revisiones.putIfAbsent(n,c);
        return c;
    }
}
