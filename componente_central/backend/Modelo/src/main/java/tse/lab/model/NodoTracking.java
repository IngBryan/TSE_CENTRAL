package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nodos_tracking")
public class NodoTracking extends NodoPeriferico {
    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "nodoTracking")
    private List<EventoTracking> eventos = new ArrayList<>();

    public NodoTracking() {
    }

    public NodoTracking(String nombre, boolean activo) {
        super(nombre, activo);
    }

    public List<EventoTracking> getEventos() {
        return eventos;
    }
}

