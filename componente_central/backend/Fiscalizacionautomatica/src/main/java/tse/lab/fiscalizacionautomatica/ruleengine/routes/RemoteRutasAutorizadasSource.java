package tse.lab.fiscalizacionautomatica.ruleengine.routes;

import java.io.IOException;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;
import tse.lab.frontofficeciudadanos.service.rutas.RutasAutorizadasRemote;

/** Adaptador EJB remoto; no modifica las rutas ni las guías. */
public final class RemoteRutasAutorizadasSource implements RutasAutorizadasSource, AutoCloseable {
    private final Context context;
    private final RutasAutorizadasRemote remote;

    public RemoteRutasAutorizadasSource(String url, String user, String password) throws IOException {
        try {
            context = WildFlyContexts.create(url, user, password);
            remote = (RutasAutorizadasRemote) context.lookup(RutasAutorizadasRemote.LOOKUP);
        } catch (NamingException e) {
            throw new IOException("No se pudo localizar el EJB de rutas autorizadas", e);
        }
    }

    /** With the remote proxy already looked up (tests). */
    RemoteRutasAutorizadasSource(Context context, RutasAutorizadasRemote remote) {
        this.context = context;
        this.remote = remote;
    }

    @Override
    public List<DatosRutaAutorizada> obtenerPorGuia(String idGuia) throws IOException {
        try {
            return remote.obtenerPorGuia(idGuia);
        } catch (RuntimeException e) {
            throw new IOException("No se pudieron consultar las rutas de la Guía " + idGuia, e);
        }
    }

    @Override
    public void close() {
        WildFlyContexts.closeQuietly(context);
    }
}
