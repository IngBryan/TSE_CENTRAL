package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Override
    public void registrarComunicaciones(TipoNodo tipo, Map<Long, Instant> ultimaPorIdExterno) {
        if (tipo == null) {
            throw new IllegalArgumentException("Falta el tipo de nodo");
        }
        if (ultimaPorIdExterno == null) {
            return;
        }
        ultimaPorIdExterno.forEach((idExterno, fecha) -> {
            if (idExterno != null && fecha != null) {
                nodos.registrarComunicacion(tipo.name(), idExterno, fecha);
            }
        });
    }
}
