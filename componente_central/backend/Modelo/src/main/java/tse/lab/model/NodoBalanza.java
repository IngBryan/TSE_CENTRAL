package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.DiscriminatorValue;

@Entity
@Table(name = "nodos_balanza")
@DiscriminatorValue("BALANZA")
public class NodoBalanza extends NodoPeriferico {
    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "nodoBalanza")
    private List<EventoPesajes> eventos = new ArrayList<>();

    public NodoBalanza() {
    }

    public NodoBalanza(
        String nombre,
        String ubicacion,
        String idExterno) {

        super(nombre, ubicacion, idExterno);
    }

    public List<EventoPesajes> getEventos() {
        return eventos;
    }
}

