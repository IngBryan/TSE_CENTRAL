package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Instantánea de las dos muestras y del conjunto completo de rutas consultadas. */
public final class PruebaDesvioRemota implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Instant instantePareja;
    private final double latConfirmante, lonConfirmante, latPareja, lonPareja;
    private final double distanciaConfirmante, distanciaPareja, umbralMetros;
    private final long ventanaSegundos;
    private final List<String> hashesRutas;

    public PruebaDesvioRemota(Instant instantePareja, double latConfirmante, double lonConfirmante,
            double latPareja, double lonPareja, double distanciaConfirmante, double distanciaPareja,
            double umbralMetros, long ventanaSegundos, List<String> hashesRutas) {
        this.instantePareja = instantePareja;
        this.latConfirmante = latConfirmante; this.lonConfirmante = lonConfirmante;
        this.latPareja = latPareja; this.lonPareja = lonPareja;
        this.distanciaConfirmante = distanciaConfirmante; this.distanciaPareja = distanciaPareja;
        this.umbralMetros = umbralMetros; this.ventanaSegundos = ventanaSegundos;
        this.hashesRutas = hashesRutas == null ? null : List.copyOf(hashesRutas);
    }
    public Instant getInstantePareja() { return instantePareja; }
    public double getLatConfirmante() { return latConfirmante; }
    public double getLonConfirmante() { return lonConfirmante; }
    public double getLatPareja() { return latPareja; }
    public double getLonPareja() { return lonPareja; }
    public double getDistanciaConfirmante() { return distanciaConfirmante; }
    public double getDistanciaPareja() { return distanciaPareja; }
    public double getUmbralMetros() { return umbralMetros; }
    public long getVentanaSegundos() { return ventanaSegundos; }
    public List<String> getHashesRutas() { return hashesRutas; }
}
