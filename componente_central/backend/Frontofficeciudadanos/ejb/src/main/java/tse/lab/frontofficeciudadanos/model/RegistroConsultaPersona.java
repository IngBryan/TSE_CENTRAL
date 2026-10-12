package tse.lab.frontofficeciudadanos.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Auditoria de consultas de datos personales (Ley 18.331): quien consulto que cedula y cuando.
// Solo se agregan filas: no hay setters ni operaciones de modificacion o borrado.
@Entity
@Table(name = "consultas_persona")
public class RegistroConsultaPersona implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private String ciUsuario;
    @Column(nullable = false, updatable = false)
    private String ciConsultada;
    @Column(nullable = false, updatable = false)
    private Instant fechaHora;
    @Column(nullable = false, updatable = false)
    private String operacion;
    @Column(updatable = false)
    private String resultado;
    @Column(updatable = false)
    private String correlationId;

    protected RegistroConsultaPersona() {
    }

    public RegistroConsultaPersona(String ciUsuario, String ciConsultada, String operacion, String resultado,
            String correlationId) {
        this.ciUsuario = ciUsuario;
        this.ciConsultada = ciConsultada;
        this.operacion = operacion;
        this.resultado = resultado;
        this.correlationId = correlationId;
        this.fechaHora = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCiUsuario() {
        return ciUsuario;
    }

    public String getCiConsultada() {
        return ciConsultada;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getResultado() {
        return resultado;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
