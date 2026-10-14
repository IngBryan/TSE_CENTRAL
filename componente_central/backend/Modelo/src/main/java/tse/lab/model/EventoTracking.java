package tse.lab.model;

import java.io.Serializable;
import java.time.Instant;

// Posicion de un vehiculo reportada por el GPS del nodo de tracking. NO es una entidad JPA: esta en
// MongoDB (base main_db, coleccion tracking) y la guarda Fiscalizacionautomatica al consumir la cola
// tracking.events. Campos del documento:
//   messageId (message_id de RabbitMQ, obligatorio), externalId -> idExterno (id del evento en el nodo
//   de tracking), gpsId, licensePlate -> matricula,
//   location {type: Point, coordinates: [longitud, latitud]}, timestamp -> fechaHora
// El _id lo genera MongoDB; lo que identifica el evento es (matricula, fechaHora), con un indice unico
// { licensePlate, timestamp }. Las entidades de PostgreSQL lo referencian por esa clave
// (CasoFiscalizacion.clavesEventosTracking) y el nodo por gpsId: no hay relaciones JPA entre las dos bases.
public class EventoTracking implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String COLECCION = "tracking";

    private String messageId;
    private Long idExterno;
    // Id del GPS = id del NodoTracking (PostgreSQL) que reporto el evento
    private Long gpsId;
    private String matricula;
    private Double latitud;
    private Double longitud;
    private Instant fechaHora;

    public EventoTracking() {
    }

    public EventoTracking(String messageId, Long idExterno, Long gpsId, String matricula, Double latitud,
            Double longitud, Instant fechaHora) {
        this.messageId = messageId;
        this.idExterno = idExterno;
        this.gpsId = gpsId;
        this.matricula = matricula;
        this.latitud = latitud;
        this.longitud = longitud;
        this.fechaHora = fechaHora;
    }

    public ClaveEvento getClave() {
        return new ClaveEvento(matricula, fechaHora);
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Long getIdExterno() {
        return idExterno;
    }

    public void setIdExterno(Long idExterno) {
        this.idExterno = idExterno;
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
