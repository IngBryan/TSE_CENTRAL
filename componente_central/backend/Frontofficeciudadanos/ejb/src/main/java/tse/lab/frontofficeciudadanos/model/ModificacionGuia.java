package tse.lab.frontofficeciudadanos.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Registro de cada modificacion que el responsable le hizo a una guia (quien, cuando, que cambio). Se
// muestra en la linea de tiempo de la guia junto a los eventos que registro el chofer desde la app.
@Entity
@Table(name = "modificaciones_guia")
public class ModificacionGuia implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_guia")
    private Guia guia;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoModificacionGuia tipo;
    @Column(nullable = false)
    private String ciResponsable;
    @Column(nullable = false)
    private Instant fecha;
    // Que cambio, legible: "destino: Paysandu -> Salto; carga: 18000 -> 20000 kg"
    @Column(length = 1000)
    private String cambios;
    // Motivo de la cancelacion
    @Column(length = 500)
    private String motivo;
    private Long versionAnterior;
    private Long versionNueva;

    public ModificacionGuia() {
    }

    public ModificacionGuia(Guia guia, TipoModificacionGuia tipo, String ciResponsable, String cambios, String motivo,
            Long versionAnterior, Long versionNueva) {
        this.guia = guia;
        this.tipo = tipo;
        this.ciResponsable = ciResponsable;
        this.fecha = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.cambios = cambios;
        this.motivo = motivo;
        this.versionAnterior = versionAnterior;
        this.versionNueva = versionNueva;
    }

    public Long getId() {
        return id;
    }

    public Guia getGuia() {
        return guia;
    }

    public TipoModificacionGuia getTipo() {
        return tipo;
    }

    public String getCiResponsable() {
        return ciResponsable;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getCambios() {
        return cambios;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getVersionAnterior() {
        return versionAnterior;
    }

    public Long getVersionNueva() {
        return versionNueva;
    }
}
