package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

// Pista de auditoria de solo agregado de los casos de fiscalizacion: quien hizo que accion, con que
// fundamento y cuando. No tiene setters, todas las columnas son updatable = false y el entity listener
// rechaza update y delete: desde la aplicacion solo se puede insertar y leer.
// El caso se guarda por id (sin FK de JPA) para que el registro no dependa del ciclo de vida del caso.
// usuario: ci del funcionario, o SISTEMA para las acciones automaticas (creacion, notificacion).
@Entity
@Table(name = "auditoria_casos", indexes = @Index(name = "idx_auditoria_casos_caso", columnList = "id_caso"))
public class RegistroAuditoriaCaso implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String SISTEMA = "SISTEMA";
    public static final int MAX_FUNDAMENTO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_caso", nullable = false, updatable = false)
    private Long idCaso;

    @Column(nullable = false, updatable = false)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AccionAuditoriaCaso accion;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private EstadoCaso estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EstadoCaso estadoNuevo;

    @Column(length = MAX_FUNDAMENTO, updatable = false)
    private String fundamento;

    @Column(nullable = false, updatable = false)
    private Instant fechaHora;

    @Column(updatable = false)
    private String correlationId;

    protected RegistroAuditoriaCaso() {
    }

    public RegistroAuditoriaCaso(Long idCaso, String usuario, AccionAuditoriaCaso accion, EstadoCaso estadoAnterior,
            EstadoCaso estadoNuevo, String fundamento, String correlationId) {
        this.idCaso = idCaso;
        this.usuario = usuario;
        this.accion = accion;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.fundamento = fundamento;
        this.correlationId = correlationId;
        this.fechaHora = Instant.now();
    }

    @PreUpdate
    @PreRemove
    void soloAgregado() {
        throw new UnsupportedOperationException("La pista de auditoria de casos es de solo agregado");
    }

    public Long getId() {
        return id;
    }

    public Long getIdCaso() {
        return idCaso;
    }

    public String getUsuario() {
        return usuario;
    }

    public AccionAuditoriaCaso getAccion() {
        return accion;
    }

    public EstadoCaso getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoCaso getEstadoNuevo() {
        return estadoNuevo;
    }

    public String getFundamento() {
        return fundamento;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
