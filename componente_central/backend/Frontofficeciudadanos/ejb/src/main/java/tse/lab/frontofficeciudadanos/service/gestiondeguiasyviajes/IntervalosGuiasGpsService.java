package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.EventoViajeDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.model.Guia;
import tse.lab.model.TipoEventoViaje;

/** Fuente de ventanas GPS para Fiscalización y para consultas históricas de Monitoreo. */
@Stateless
public class IntervalosGuiasGpsService implements IntervalosGuiasGpsRemote {

    @EJB private GuiaDAO guias;
    @EJB private EventoViajeDAO eventos;

    @Override
    public List<IntervaloGuiaGps> buscar(String matricula, Instant desde, Instant hasta) {
        if (matricula == null || matricula.isBlank()) {
            return List.of();
        }
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("Se requiere un intervalo [desde, hasta) valido");
        }
        List<Guia> candidatas = guias.buscarCandidatasGps(matricula.strip(), hasta);
        if (candidatas.isEmpty()) {
            return List.of();
        }
        Map<String, Instant> inicios = new HashMap<>();
        Map<String, Instant> fines = new HashMap<>();
        for (Object[] fila : eventos.buscarLimitesAplicados(candidatas)) {
            String id = (String) fila[0];
            TipoEventoViaje tipo = (TipoEventoViaje) fila[1];
            Instant instante = (Instant) fila[2];
            if (tipo == TipoEventoViaje.INICIO) {
                inicios.merge(id, instante, (a, b) -> a.isBefore(b) ? a : b);
            } else if (tipo == TipoEventoViaje.FIN) {
                fines.merge(id, instante, (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        return candidatas.stream()
                // Se conserva FINALIZADA sin FIN aplicado como marcador de ventana
                // temporal incierta: el asociador devolverá NO_DETERMINABLE.
                .map(g -> new IntervaloGuiaGps(g.getId(), g.getVehiculo().getMatricula(), g.getEstado().name(),
                        g.getFechaAsignacion(), inicios.get(g.getId()), fines.get(g.getId())))
                .filter(i -> i.getAsignacion() != null && i.getAsignacion().isBefore(hasta)
                        && (i.getFinAplicado() == null || i.getFinAplicado().isAfter(desde)))
                .toList();
    }
}
