package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;

/** Instantánea probatoria: coordenadas WGS84, distancias métricas aproximadas y SHA de rutas. */
@Embeddable
public class EvidenciaGeometricaDesvio implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "matricula", nullable = false)
    private String matricula;
    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;
    @Column(name = "fecha_pareja", nullable = false)
    private Instant fechaPareja;
    @Column(name = "lat_confirmante", nullable = false)
    private double latConfirmante;
    @Column(name = "lon_confirmante", nullable = false)
    private double lonConfirmante;
    @Column(name = "lat_pareja", nullable = false)
    private double latPareja;
    @Column(name = "lon_pareja", nullable = false)
    private double lonPareja;
    @Column(name = "dist_confirmante_m", nullable = false)
    private double distanciaConfirmante;
    @Column(name = "dist_pareja_m", nullable = false)
    private double distanciaPareja;
    @Column(name = "umbral_m", nullable = false)
    private double umbralMetros;
    @Column(name = "ventana_seg", nullable = false)
    private long ventanaSegundos;
    @Column(name = "rutas_sha", nullable = false, length = 256)
    private String rutasSha;

    protected EvidenciaGeometricaDesvio() { }

    public EvidenciaGeometricaDesvio(String matricula, Instant fechaHora, Instant fechaPareja,
            double latConfirmante, double lonConfirmante, double latPareja, double lonPareja,
            double distanciaConfirmante, double distanciaPareja, double umbralMetros,
            long ventanaSegundos, String rutasSha) {
        this.matricula = matricula; this.fechaHora = fechaHora; this.fechaPareja = fechaPareja;
        this.latConfirmante = latConfirmante; this.lonConfirmante = lonConfirmante;
        this.latPareja = latPareja; this.lonPareja = lonPareja;
        this.distanciaConfirmante = distanciaConfirmante; this.distanciaPareja = distanciaPareja;
        this.umbralMetros = umbralMetros; this.ventanaSegundos = ventanaSegundos;
        this.rutasSha = rutasSha;
    }
    public String getMatricula() { return matricula; }
    public Instant getFechaHora() { return fechaHora; }
    public Instant getFechaPareja() { return fechaPareja; }
    public double getLatConfirmante() { return latConfirmante; }
    public double getLonConfirmante() { return lonConfirmante; }
    public double getLatPareja() { return latPareja; }
    public double getLonPareja() { return lonPareja; }
    public double getDistanciaConfirmante() { return distanciaConfirmante; }
    public double getDistanciaPareja() { return distanciaPareja; }
    public double getUmbralMetros() { return umbralMetros; }
    public long getVentanaSegundos() { return ventanaSegundos; }
    public String getRutasSha() { return rutasSha; }
}
