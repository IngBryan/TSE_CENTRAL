package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

/** Error transitorio de infraestructura, distinto de un viaje sin posiciones. */
public final class SeguimientoNoDisponibleException extends RuntimeException {
    public SeguimientoNoDisponibleException(String mensaje, Throwable causa) { super(mensaje, causa); }
    public SeguimientoNoDisponibleException(String mensaje) { super(mensaje); }
}
