package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Misma política temporal para Fiscalización y Monitoreo. No implica infracción
 * ni modifica los eventos de Tracking; devuelve una asociación recalculable.
 */
public final class AsociacionTemporalGuia {
    private AsociacionTemporalGuia() { }

    public enum Estado { ASOCIADO, SIN_GUIA, AMBIGUO, NO_DETERMINABLE }

    public record Resultado(Estado estado, String idGuia) {
        public Resultado {
            Objects.requireNonNull(estado, "estado");
            if ((estado == Estado.ASOCIADO) != (idGuia != null && !idGuia.isBlank())) {
                throw new IllegalArgumentException("Resultado de asociación inconsistente");
            }
        }
        public boolean correspondeA(String id) {
            return estado == Estado.ASOCIADO && idGuia.equals(id);
        }
    }

    public static Resultado resolver(String matricula, Instant instante, List<IntervaloGuiaGps> intervalos) {
        Objects.requireNonNull(matricula, "matricula");
        Objects.requireNonNull(instante, "instante");
        Objects.requireNonNull(intervalos, "intervalos");
        List<IntervaloGuiaGps> candidatas = intervalos.stream()
                .filter(i -> i.getMatricula().equals(matricula)
                        && !instante.isBefore(i.getAsignacion())
                        && (i.getFinAplicado() == null || instante.isBefore(i.getFinAplicado())))
                .toList();
        if (candidatas.stream().anyMatch(i -> "FINALIZADA".equals(i.getEstado())
                && i.getFinAplicado() == null)) {
            return new Resultado(Estado.NO_DETERMINABLE, null);
        }
        List<IntervaloGuiaGps> iniciadas = candidatas.stream()
                .filter(i -> i.getInicioAplicado() != null && !instante.isBefore(i.getInicioAplicado()))
                .toList();
        if (iniciadas.size() == 1) return new Resultado(Estado.ASOCIADO, iniciadas.getFirst().getIdGuia());
        if (iniciadas.size() > 1) return new Resultado(Estado.AMBIGUO, null);
        if (candidatas.size() == 1) return new Resultado(Estado.ASOCIADO, candidatas.getFirst().getIdGuia());
        return new Resultado(candidatas.isEmpty() ? Estado.SIN_GUIA : Estado.AMBIGUO, null);
    }
}
