package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "eventos_tracking")
public class EventoTracking implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double latitud;

    private Double longitud;

    private String matricula;

    private Instant fechaHora;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_nodo_tracking", nullable = false)
    private NodoTracking nodoTracking;

    @ManyToMany(mappedBy = "eventosTracking")
    private List<CasoFiscalizacion> casosFiscalizacion = new ArrayList<>();

    public EventoTracking() {
    }

    public EventoTracking(Double latitud, Double longitud, String matricula, Instant fechaHora, NodoTracking nodoTracking) {
        this.latitud = latitud;
        this.longitud = longitud;
        this.matricula = matricula;
        this.fechaHora = fechaHora;
        this.nodoTracking = nodoTracking;
    }

    public Long getId() {
        return id;
    }

    public Double getLatitud() {
        return latitud;
    }

    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(Instant fechaHora) {
        this.fechaHora = fechaHora;
    }

    public NodoTracking getNodoTracking() {
        return nodoTracking;
    }

    public void setNodoTracking(NodoTracking nodoTracking) {
        this.nodoTracking = nodoTracking;
    }

    public List<CasoFiscalizacion> getCasosFiscalizacion() {
        return casosFiscalizacion;
    }
}

