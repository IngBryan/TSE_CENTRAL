package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// Sus eventos (EventoTracking) estan en MongoDB, coleccion tracking, con el id del nodo en gpsId:
// no hay relacion JPA.
@Entity
@Table(name = "nodos_tracking")
public class NodoTracking extends NodoPeriferico {
    private static final long serialVersionUID = 1L;

    public NodoTracking() {
    }

    public NodoTracking(String nombre, boolean activo) {
        super(nombre, activo);
    }
}
