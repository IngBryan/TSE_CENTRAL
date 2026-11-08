package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervalosGuiasGpsRemote;

/** Adapter EJB remoto a los límites temporales de PostgreSQL. */
public final class RemoteIntervalosGuiasGpsSource implements IntervalosGuiasGpsSource, AutoCloseable {
    private final Context context;
    private final IntervalosGuiasGpsRemote remote;

    public RemoteIntervalosGuiasGpsSource(String url, String user, String password) throws IOException {
        try {
            context = WildFlyContexts.create(url, user, password);
            remote = (IntervalosGuiasGpsRemote) context.lookup(IntervalosGuiasGpsRemote.LOOKUP);
        } catch (NamingException e) {
            throw new IOException("No se pudo localizar el EJB de intervalos de Guías", e);
        }
    }

    /** With the remote proxy already looked up (tests). */
    RemoteIntervalosGuiasGpsSource(Context context, IntervalosGuiasGpsRemote remote) {
        this.context = context;
        this.remote = remote;
    }

    @Override
    public List<IntervaloGuiaGps> buscar(String matricula, Instant desde, Instant hasta) throws IOException {
        try {
            return remote.buscar(matricula, desde, hasta);
        } catch (RuntimeException e) {
            throw new IOException("No se pudieron consultar los intervalos de las Guías", e);
        }
    }

    @Override
    public void close() {
        WildFlyContexts.closeQuietly(context);
    }
}
