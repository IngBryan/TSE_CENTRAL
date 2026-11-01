package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.io.Serializable;
import java.time.Instant;

/** Solo coordenadas y clave; no copia ni persiste el documento MongoDB. */
public final class PosicionSinGuiaRemota implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matricula;
    private final Instant fechaHora;
    private final double latitud;
    private final double longitud;
    public PosicionSinGuiaRemota(String matricula, Instant fechaHora, double latitud, double longitud) {
        this.matricula = matricula; this.fechaHora = fechaHora;
        this.latitud = latitud; this.longitud = longitud;
    }
    public String getMatricula() { return matricula; }
    public Instant getFechaHora() { return fechaHora; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
}
