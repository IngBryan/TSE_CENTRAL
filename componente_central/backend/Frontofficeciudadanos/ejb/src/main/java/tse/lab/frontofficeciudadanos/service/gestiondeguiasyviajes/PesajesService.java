package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.EventoPesajesDAO;
import tse.lab.frontofficeciudadanos.data.NodoBalanzaDAO;
import tse.lab.model.ClaveEvento;
import tse.lab.model.EventoPesajes;
import tse.lab.model.NodoBalanza;

// Pesajes que trae el poller de la fiscalizacion automatica desde el nodo de balanzas. Quedan pendientes
// de deteccion hasta que el poller los evalua y los marca.
@Stateless
public class PesajesService implements PesajesRemote {

    private static final Logger LOG = Logger.getLogger(PesajesService.class.getName());

    @EJB
    private EventoPesajesDAO pesajes;
    @EJB
    private NodoBalanzaDAO balanzas;

    @Override
    public int guardarPesajes(List<DatosPesaje> nuevos) {
        int guardados = 0;
        for (DatosPesaje datos : nuevos) {
            if (pesajes.buscar(new ClaveEvento(datos.getMatricula(), datos.getFechaHora())) != null) {
                continue;
            }
            NodoBalanza balanza = balanzas.buscarPorIdExterno(datos.getIdExternoBalanza());
            if (balanza == null) {
                LOG.warning("Pesaje de una balanza no dada de alta, se ignora: " + datos);
                continue;
            }
            pesajes.guardar(new EventoPesajes(datos.getIdExterno(), datos.getMatricula(), datos.getFechaHora(),
                    datos.getPesoKg(), balanza));
            guardados++;
        }
        return guardados;
    }

    @Override
    public List<DatosPesaje> listarPendientes(int limite) {
        return pesajes.listarPendientes(limite).stream()
                .map(p -> new DatosPesaje(p.getIdExterno(), p.getNodoBalanza().getIdExterno(), p.getMatricula(),
                        p.getFechaHora(), p.getPeso()))
                .toList();
    }

    @Override
    public void marcarDetectado(String matricula, Instant fechaHora) {
        EventoPesajes pesaje = pesajes.buscar(new ClaveEvento(matricula, fechaHora));
        if (pesaje != null) {
            pesaje.marcarDetectado();
        }
    }
}
