package tse.lab.fiscalizacionautomatica.ruleengine.nodes;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.DatosNodo;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.NodosActivosRemote;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.TipoNodo;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;

/**
 * Active peripheral nodes (activo = true), through the Backofficeautoridadesadministradores remote EJB.
 * Nodes are identified by their external id: the id in their own system.
 */
public class ActiveNodesClient implements AutoCloseable {

    private final Context context;
    private final NodosActivosRemote nodes;

    /** @param url WildFly HTTP port, e.g. remote+http://wildfly:8080 */
    public ActiveNodesClient(String url, String user, String password) throws IOException {
        try {
            context = WildFlyContexts.create(url, user, password);
            nodes = (NodosActivosRemote) context.lookup(NodosActivosRemote.LOOKUP);
        } catch (NamingException e) {
            throw new IOException("Could not look up " + NodosActivosRemote.LOOKUP, e);
        }
    }

    /** @return ids of the active scales in the balanzas API */
    public List<Long> findActiveScaleIds() throws IOException {
        return findActive(TipoNodo.BALANZA);
    }

    /** @return ids of the active GPS in the tracking node (gpsId of its events) */
    public Set<Long> findActiveGpsIds() throws IOException {
        return Set.copyOf(findActive(TipoNodo.TRACKING));
    }

    private List<Long> findActive(TipoNodo type) throws IOException {
        try {
            return nodes.listarNodos().stream()
                    .filter(node -> node.getTipo() == type)
                    .map(DatosNodo::getIdExterno)
                    .toList();
        } catch (RuntimeException e) {
            // EJBException (WildFly down, wrong credentials, EAR not deployed) or a failure reading the answer
            throw new IOException("Remote call to NodosActivosService failed", e);
        }
    }

    @Override
    public void close() {
        WildFlyContexts.closeQuietly(context);
    }
}
