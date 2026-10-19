package tse.lab.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// El tipo es texto: el diagrama no define sus valores posibles. El estado sigue el ciclo de vida de
// EstadoCaso y solo cambia con cambiarEstado, que rechaza las transiciones no previstas.
@Entity
@Table(name = "casos_fiscalizacion")
public class CasoFiscalizacion implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descripcion;

    @Column(nullable = false)
    private String tipoCaso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCaso estado;

    @Column(nullable = false)
    private Instant fechaDeteccion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_guia", nullable = false)
    private Guia guia;

    @OneToOne(mappedBy = "casoFiscalizacion")
    private FundamentoResolucion fundamentoResolucion;

    @OneToOne(mappedBy = "casoFiscalizacion")
    private Descargo descargo;

    @ManyToMany
    @JoinTable(name = "casos_eventos_pesajes",
            joinColumns = @JoinColumn(name = "id_caso"),
            inverseJoinColumns = {
                    @JoinColumn(name = "matricula", referencedColumnName = "matricula"),
                    @JoinColumn(name = "fecha_hora", referencedColumnName = "fecha_hora") })
    private List<EventoPesajes> eventosPesajes = new ArrayList<>();

    // Los eventos de tracking estan en MongoDB (EventoTracking): aca solo se guarda la clave
    // (matricula, fecha_hora) de cada uno
    @ElementCollection
    @CollectionTable(name = "casos_eventos_tracking", joinColumns = @JoinColumn(name = "id_caso"))
    private List<ClaveEvento> clavesEventosTracking = new ArrayList<>();

    public CasoFiscalizacion() {
    }

    // Todo caso nace DETECTADA (lo crea la fiscalizacion automatica)
    public CasoFiscalizacion(String descripcion, String tipoCaso, Guia guia) {
        this.descripcion = descripcion;
        this.tipoCaso = tipoCaso;
        this.estado = EstadoCaso.DETECTADA;
        this.fechaDeteccion = Instant.now();
        this.guia = guia;
    }

    public Long getId() {
        return id;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTipoCaso() {
        return tipoCaso;
    }

    public void setTipoCaso(String tipoCaso) {
        this.tipoCaso = tipoCaso;
    }

    public EstadoCaso getEstado() {
        return estado;
    }

    // Unica forma de cambiar el estado: IllegalStateException si la transicion no esta prevista
    public void cambiarEstado(EstadoCaso nuevo) {
        if (estado == null || !estado.puedePasarA(nuevo)) {
            throw new IllegalStateException("Transicion de caso no permitida: " + estado + " -> " + nuevo);
        }
        this.estado = nuevo;
    }

    public Instant getFechaDeteccion() {
        return fechaDeteccion;
    }

    public Guia getGuia() {
        return guia;
    }

    public void setGuia(Guia guia) {
        this.guia = guia;
    }

    public FundamentoResolucion getFundamentoResolucion() {
        return fundamentoResolucion;
    }

    public void setFundamentoResolucion(FundamentoResolucion fundamentoResolucion) {
        this.fundamentoResolucion = fundamentoResolucion;
    }

    public Descargo getDescargo() {
        return descargo;
    }

    public void setDescargo(Descargo descargo) {
        this.descargo = descargo;
    }

    public List<EventoPesajes> getEventosPesajes() {
        return eventosPesajes;
    }

    public List<ClaveEvento> getClavesEventosTracking() {
        return clavesEventosTracking;
    }
}

