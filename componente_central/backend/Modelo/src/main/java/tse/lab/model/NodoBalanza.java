package tse.lab.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

// idExterno: id de la balanza en la API de balanzas (TSE_NODOS)
@Entity
@Table(name = "nodos_balanza")
@DiscriminatorValue("BALANZA")
public class NodoBalanza extends NodoPeriferico {
    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "nodoBalanza")
    private List<EventoPesajes> eventos = new ArrayList<>();

    public NodoBalanza() {
    }

    public NodoBalanza(Long idExterno, String nombre, boolean activo) {
        super(idExterno, nombre, activo);
    }

    public List<EventoPesajes> getEventos() {
        return eventos;
    }
}

