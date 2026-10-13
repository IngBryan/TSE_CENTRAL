package tse.lab.model;

import java.io.Serializable;
import java.time.Instant;

// Posicion de un vehiculo reportada por el GPS del nodo de tracking. NO es una entidad JPA: esta en
// MongoDB (base main_db, coleccion tracking) y la guarda Fiscalizacionautomatica al consumir la cola
// tracking.events. Campos del documento:
//   _id -> id (message_id de RabbitMQ), eventId, gpsId, licensePlate -> matricula,
//   location {type: Point, coordinates: [longitud, latitud]}, timestamp -> fechaHora
// Las entidades de PostgreSQL lo referencian por id (CasoFiscalizacion.idsEventosTracking) y el nodo
// queda referenciado por gpsId: no hay relaciones JPA entre las dos bases.
public class EventoTracking implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String COLECCION = "tracking";

    private String id;
    private Long eventId;
    // Id del GPS = id del NodoTracking (PostgreSQL) que reporto el evento
    private Long gpsId;
    private String matricula;
    private Double latitud;
    private Double longitud;
    private Instant fechaHora;

    public EventoTracking() {
    }

    public EventoTracking(String id, Long eventId, Long gpsId, String matricula, Double latitud, Double longitud,
            Instant fechaHora) {
        this.id = id;
        this.eventId = eventId;
        this.gpsId = gpsId;
        this.matricula = matricula;
        this.latitud = latitud;
        this.longitud = longitud;
        this.fechaHora = fechaHora;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getGpsId() {
        return gpsId;
    }

    public void setGpsId(Long gpsId) {
        this.gpsId = gpsId;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
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

    public Instant getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(Instant fechaHora) {
        this.fechaHora = fechaHora;
    }
}
