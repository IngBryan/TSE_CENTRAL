package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.backofficeautoridadesadministradores.data.NodoPerifericoDAO;

// Nodos activos para la fiscalizacion automatica: el poller consulta solo las balanzas activas.
@Stateless
public class NodosActivosService implements NodosActivosRemote {

    @EJB
    private NodoPerifericoDAO nodos;

    @Override
    public List<DatosNodo> listarNodos() {
        return nodos.listarActivos()
                .stream()
                .map(DatosNodoMapper::desde)
                .toList();
    }
}
