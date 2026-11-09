package tse.lab.fiscalizacionautomatica.ruleengine.config;

import java.io.IOException;
import javax.naming.*;
import tse.lab.reglas.ConfiguracionReglas;
import tse.lab.backofficeautoridadesadministradores.service.reglas.ConfiguracionReglasRemote;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;

public class ReglasClient implements AutoCloseable {
    private final Context context;
    private final ConfiguracionReglasRemote remote;
    public ReglasClient(String url, String user, String password) throws IOException {
        Context nuevo=null;
        try {
            nuevo=WildFlyContexts.create(url, user, password);
            remote=(ConfiguracionReglasRemote) nuevo.lookup(ConfiguracionReglasRemote.LOOKUP);
            context=nuevo;
        } catch (NamingException e) {
            if (nuevo!=null) WildFlyContexts.closeQuietly(nuevo);
            throw new IOException("No se pudo consultar la configuración de reglas", e);
        }
    }
    public ConfiguracionReglas vigente() throws IOException {
        try { return remote.obtenerVigente(); }
        catch (RuntimeException e) { throw new IOException("Configuración de reglas no disponible", e); }
    }
    public ConfiguracionReglas revision(long n) throws IOException {
        try { return remote.obtenerRevision(n); }
        catch (RuntimeException e) { throw new IOException("Revisión no disponible: " + n, e); }
    }
    public void close() { WildFlyContexts.closeQuietly(context); }
}
