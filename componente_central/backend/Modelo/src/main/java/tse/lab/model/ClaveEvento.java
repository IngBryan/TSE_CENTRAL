package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

// Lo que identifica un evento de un nodo periferico (pesaje o tracking): el vehiculo y el momento.
// Es la clave primaria de EventoPesajes (@IdClass) y la referencia a los eventos de tracking (MongoDB)
// desde CasoFiscalizacion.
@Embeddable
public class ClaveEvento implements Serializable {
    private static final long serialVersionUID = 1L;

    private String matricula;

    @Column(name = "fecha_hora")
    private Instant fechaHora;

    public ClaveEvento() {
    }

    public ClaveEvento(String matricula, Instant fechaHora) {
        this.matricula = matricula;
        this.fechaHora = fechaHora;
    }

    public String getMatricula() {
        return matricula;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClaveEvento otra && Objects.equals(matricula, otra.matricula)
                && Objects.equals(fechaHora, otra.fechaHora);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matricula, fechaHora);
    }
}
