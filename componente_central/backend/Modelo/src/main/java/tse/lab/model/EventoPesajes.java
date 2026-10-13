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
@Table(name = "eventos_pesajes")
public class EventoPesajes implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long peso;

    private String matricula;

    private Instant fechaHora;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_nodo_balanza", nullable = false)
    private NodoBalanza nodoBalanza;

    @ManyToMany(mappedBy = "eventosPesajes")
    private List<CasoFiscalizacion> casosFiscalizacion = new ArrayList<>();

    public EventoPesajes() {
    }

    public EventoPesajes(long peso, String matricula, Instant fechaHora, NodoBalanza nodoBalanza) {
        this.peso = peso;
        this.matricula = matricula;
        this.fechaHora = fechaHora;
        this.nodoBalanza = nodoBalanza;
    }

    public Long getId() {
        return id;
    }

    public long getPeso() {
        return peso;
    }

    public void setPeso(long peso) {
        this.peso = peso;
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

    public NodoBalanza getNodoBalanza() {
        return nodoBalanza;
    }

    public void setNodoBalanza(NodoBalanza nodoBalanza) {
        this.nodoBalanza = nodoBalanza;
    }

    public List<CasoFiscalizacion> getCasosFiscalizacion() {
        return casosFiscalizacion;
    }
}

