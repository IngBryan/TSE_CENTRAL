package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.backofficeautoridadesadministradores.data.NodoPerifericoDAO;
import tse.lab.model.NodoBalanza;
import tse.lab.model.NodoPeriferico;

// Nodos activos para la fiscalizacion automatica: el poller consulta solo las balanzas activas.
@Stateless
public class NodosActivosService implements NodosActivosRemote {

    @EJB
    private NodoPerifericoDAO nodos;

    @Override
    public List<DatosNodo> listarNodos() {
        return nodos.listarActivos().stream()
                .map(NodosActivosService::datos)
                .toList();
    }

    private static DatosNodo datos(NodoPeriferico n) {
        TipoNodo tipo = n instanceof NodoBalanza ? TipoNodo.BALANZA : TipoNodo.TRACKING;
        return new DatosNodo(n.getId(), n.getNombre(), tipo);
    }
}
