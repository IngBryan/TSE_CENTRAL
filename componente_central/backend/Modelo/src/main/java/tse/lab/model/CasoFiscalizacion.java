package tse.lab.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
import java.util.ArrayList;
import java.util.List;

// Tipo y estado son textos: el diagrama no define sus valores posibles.
@Entity
@Table(name = "casos_fiscalizacion")
public class CasoFiscalizacion implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descripcion;

    private String tipoCaso;

    private String estado;

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
            inverseJoinColumns = @JoinColumn(name = "id_evento_pesaje"))
    private List<EventoPesajes> eventosPesajes = new ArrayList<>();

    // Los eventos de tracking estan en MongoDB (EventoTracking): aca solo se guarda el _id de cada uno
    @ElementCollection
    @CollectionTable(name = "casos_eventos_tracking", joinColumns = @JoinColumn(name = "id_caso"))
    @Column(name = "id_evento_tracking")
    private List<String> idsEventosTracking = new ArrayList<>();

    public CasoFiscalizacion() {
    }

    public CasoFiscalizacion(String descripcion, String tipoCaso, String estado, Guia guia) {
        this.descripcion = descripcion;
        this.tipoCaso = tipoCaso;
        this.estado = estado;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
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

    public List<String> getIdsEventosTracking() {
        return idsEventosTracking;
    }
}

