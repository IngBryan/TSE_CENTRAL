package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import java.time.Instant;
import java.util.List;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.AsociacionTemporalGuia;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

/** Filtro y cursor sobre candidatas GPS; no depende del estado actual de la Guía. */
public final class SegmentoSeguimientoGps {
    private SegmentoSeguimientoGps() { }

    public record Pagina(List<PuntoGpsSeguimiento> posiciones, Instant siguienteDesde, boolean hayMas) { }

    public static Pagina filtrar(String idGuia, List<IntervaloGuiaGps> intervalos,
            List<PuntoGpsSeguimiento> candidatas, int limite) {
        List<PuntoGpsSeguimiento> examinadas = candidatas.stream().limit(limite).toList();
        List<PuntoGpsSeguimiento> asociadas = examinadas.stream()
                .filter(p -> AsociacionTemporalGuia.resolver(p.matricula(), p.fechaHora(), intervalos)
                        .correspondeA(idGuia))
                .toList();
        boolean hayMas = candidatas.size() > limite;
        Instant siguiente = hayMas && !examinadas.isEmpty()
                ? examinadas.getLast().fechaHora().plusMillis(1) : null;
        return new Pagina(asociadas, siguiente, hayMas);
    }
}
