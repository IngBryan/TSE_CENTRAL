package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nodos_balanza")
public class NodoBalanza extends NodoPeriferico {
    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "nodoBalanza")
    private List<EventoPesajes> eventos = new ArrayList<>();

    public NodoBalanza() {
    }

    public NodoBalanza(String nombre, boolean activo) {
        super(nombre, activo);
    }

    public List<EventoPesajes> getEventos() {
        return eventos;
    }
}

