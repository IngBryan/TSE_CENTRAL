package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import java.time.Instant;
import java.util.List;

/** Contrato de solo lectura; las pruebas pueden inyectar una fuente en memoria. */
public interface TrackingSeguimientoSource {
    /** [desde,hasta), orden ascendente, máximo limite eventos candidatos. */
    List<PuntoGpsSeguimiento> buscar(String matricula, Instant desde, Instant hasta, int limite);
    /** Puntos recientes en orden descendente, para mostrar la última posición válida de la Guía. */
    List<PuntoGpsSeguimiento> recientes(String matricula, Instant desde, Instant hasta, int limite);
}
