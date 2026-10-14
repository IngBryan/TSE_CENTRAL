package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// La clave es (matricula, fecha_hora): si el poller trae el mismo pesaje dos veces no se duplica.
// id_externo es el id del pesaje en el nodo de balanzas.
@Entity
@Table(name = "eventos_pesajes")
@IdClass(ClaveEvento.class)
public class EventoPesajes implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String matricula;

    @Id
    @Column(name = "fecha_hora")
    private Instant fechaHora;

    @Column(name = "id_externo")
    private Long idExterno;

    private long peso;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_nodo_balanza", nullable = false)
    private NodoBalanza nodoBalanza;

    @ManyToMany(mappedBy = "eventosPesajes")
    private List<CasoFiscalizacion> casosFiscalizacion = new ArrayList<>();

    public EventoPesajes() {
    }

    public EventoPesajes(Long idExterno, String matricula, Instant fechaHora, long peso, NodoBalanza nodoBalanza) {
        this.idExterno = idExterno;
        this.matricula = matricula;
        this.fechaHora = fechaHora;
        this.peso = peso;
        this.nodoBalanza = nodoBalanza;
    }

    public ClaveEvento getClave() {
        return new ClaveEvento(matricula, fechaHora);
    }

    public Long getIdExterno() {
        return idExterno;
    }

    public void setIdExterno(Long idExterno) {
        this.idExterno = idExterno;
    }

    public String getMatricula() {
        return matricula;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public long getPeso() {
        return peso;
    }

    public void setPeso(long peso) {
        this.peso = peso;
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
