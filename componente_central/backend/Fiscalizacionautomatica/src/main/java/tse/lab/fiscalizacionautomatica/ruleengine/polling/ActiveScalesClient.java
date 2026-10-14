package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import jakarta.ejb.EJBException;
import java.io.IOException;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.DatosNodo;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.NodosActivosRemote;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.TipoNodo;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;

/** Active scales (NodoBalanza with activo = true), through the Backofficeautoridadesadministradores remote EJB. */
public class ActiveScalesClient implements AutoCloseable {

    private final Context context;
    private final NodosActivosRemote nodes;

    /** @param url WildFly HTTP port, e.g. remote+http://wildfly:8080 */
    public ActiveScalesClient(String url, String user, String password) throws IOException {
        try {
            context = WildFlyContexts.create(url, user, password);
            nodes = (NodosActivosRemote) context.lookup(NodosActivosRemote.LOOKUP);
        } catch (NamingException e) {
            throw new IOException("Could not look up " + NodosActivosRemote.LOOKUP, e);
        }
    }

    /** @return ids of the active scales, the same ids the balanzas API uses */
    public List<Long> findActiveScaleIds() throws IOException {
        try {
            return nodes.listarNodos().stream()
                    .filter(node -> node.tipo() == TipoNodo.BALANZA)
                    .map(DatosNodo::id)
                    .toList();
        } catch (EJBException e) {
            // WildFly down, wrong credentials or the EAR not deployed
            throw new IOException("Remote call to NodosActivosService failed", e);
        }
    }

    @Override
    public void close() {
        WildFlyContexts.closeQuietly(context);
    }
}
