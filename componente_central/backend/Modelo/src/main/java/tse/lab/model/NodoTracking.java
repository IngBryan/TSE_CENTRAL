
package tse.lab.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// idExterno: id del GPS en el nodo de tracking, que llega como gpsId
// en sus eventos. Los eventos (EventoTracking) estan en MongoDB,
// coleccion tracking: no hay relacion JPA.
@Entity
@Table(name = "nodos_tracking")
@DiscriminatorValue("TRACKING")
public class NodoTracking extends NodoPeriferico {

    private static final long serialVersionUID = 1L;

    public NodoTracking() {
    }

    public NodoTracking(
            String nombre,
            String ubicacion,
            Long idExterno) {

        super(nombre, ubicacion, idExterno);
    }
}
