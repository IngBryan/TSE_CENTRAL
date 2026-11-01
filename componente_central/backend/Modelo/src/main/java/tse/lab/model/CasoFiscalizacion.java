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
import jakarta.persistence.UniqueConstraint;
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

    // false al crearse; pasa a true cuando el usuario de la empresa ve la notificacion del caso
    @Column(name = "notificacion_vista", nullable = false)
    private boolean notificacionVista;

    // El vehiculo del caso y, a traves de el, la empresa a notificar. Lo tienen todos los casos que crea la
    // fiscalizacion automatica; la columna admite null por los casos cargados antes de agregarla.
    @ManyToOne
    @JoinColumn(name = "id_vehiculo")
    private Vehiculo vehiculo;

    // Opcional: un caso de Viaje sin guia no tiene guia
    @ManyToOne
    @JoinColumn(name = "id_guia")
    private Guia guia;

    @OneToOne(mappedBy = "casoFiscalizacion")
    private FundamentoResolucion fundamentoResolucion;

    @OneToOne(mappedBy = "casoFiscalizacion")
    private Descargo descargo;

    // Una evidencia puede respaldar varios casos; un vínculo (caso, evento) es único.
    @ManyToMany
    @JoinTable(name = "casos_eventos_pesajes",
            joinColumns = @JoinColumn(name = "id_caso"),
            inverseJoinColumns = {
                    @JoinColumn(name = "matricula", referencedColumnName = "matricula"),
                    @JoinColumn(name = "fecha_hora", referencedColumnName = "fecha_hora") },
            uniqueConstraints = @UniqueConstraint(name = "uk_caso_pesaje", columnNames = {
                    "id_caso", "matricula", "fecha_hora" }))
    private List<EventoPesajes> eventosPesajes = new ArrayList<>();

    // Los eventos de tracking estan en MongoDB (EventoTracking): aca solo se guarda la clave
    // (matricula, fecha_hora) de cada uno
    @ElementCollection
    @CollectionTable(name = "casos_eventos_tracking", joinColumns = @JoinColumn(name = "id_caso"),
            uniqueConstraints = @UniqueConstraint(name = "uk_caso_tracking", columnNames = {
                    "id_caso", "matricula", "fecha_hora" }))
    private List<ClaveEvento> clavesEventosTracking = new ArrayList<>();

    public CasoFiscalizacion() {
    }

    // Todo caso nace DETECTADA (lo crea la fiscalizacion automatica). El vehiculo es el de la guia.
    public CasoFiscalizacion(String descripcion, String tipoCaso, Guia guia) {
        this(descripcion, tipoCaso, guia == null ? null : guia.getVehiculo(), guia);
    }

    public CasoFiscalizacion(String descripcion, String tipoCaso, Vehiculo vehiculo, Guia guia) {
        this.descripcion = descripcion;
        this.tipoCaso = tipoCaso;
        this.estado = EstadoCaso.DETECTADA;
        this.fechaDeteccion = Instant.now();
        this.vehiculo = vehiculo;
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

    public boolean isNotificacionVista() {
        return notificacionVista;
    }

    public void marcarNotificacionVista() {
        this.notificacionVista = true;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
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

