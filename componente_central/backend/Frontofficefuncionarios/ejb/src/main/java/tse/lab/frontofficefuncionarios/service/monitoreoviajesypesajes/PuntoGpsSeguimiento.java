package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import java.time.Instant;

/** Punto recibido de Tracking, sin adjudicarle una Guía en el documento MongoDB. */
public record PuntoGpsSeguimiento(String messageId, long gpsId, String matricula,
        double latitud, double longitud, Instant fechaHora) { }
