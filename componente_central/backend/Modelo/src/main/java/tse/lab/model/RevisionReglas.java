package tse.lab.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import tse.lab.reglas.ConfiguracionReglas;

/** Los parámetros no se modifican. publicadaEn funciona como outbox transaccional. */
@Entity
@Table(name = "reglas_fiscalizacion_revisiones")
public class RevisionReglas {
    @Id private Long revision;
    @Column(name="tolerancia_porcentaje", nullable=false, precision=5, scale=2) private BigDecimal porcentaje;
    @Column(name="tolerancia_kg", nullable=false) private int kg;
    @Column(name="distancia_metros", nullable=false) private int metros;
    @Column(name="ventana_segundos", nullable=false) private int segundos;
    @Column(name="creada_en", nullable=false) private Instant creadaEn;
    @Column(nullable=false, length=64) private String usuario;
    @Column(nullable=false, length=500) private String fundamento;
    @Column(name="correlation_id", length=64) private String correlationId;
    @Column(name="publicada_en") private Instant publicadaEn;
    protected RevisionReglas() { }
    public RevisionReglas(ConfiguracionReglas c, String correlationId) {
        revision=c.getRevision(); porcentaje=c.getToleranciaCargaPorcentaje(); kg=c.getToleranciaCargaKg();
        metros=c.getDistanciaDesvioMetros(); segundos=c.getVentanaDesvioSegundos();
        creadaEn=Instant.parse(c.getFechaActualizacion()); usuario=c.getUsuario(); fundamento=c.getFundamento();
        this.correlationId=correlationId;
    }
    public ConfiguracionReglas datos() {
        return new ConfiguracionReglas(revision, porcentaje, kg, metros, segundos, creadaEn.toString(),
                usuario, fundamento, publicadaEn != null);
    }
    public boolean pendiente() { return publicadaEn == null; }
    public void publicada() { publicadaEn=Instant.now(); }
    public String getCorrelationId() { return correlationId; }
}
