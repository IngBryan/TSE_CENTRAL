package tse.lab.frontofficefuncionarios.service.gestioncasos;

// Tipos de caso que crea la fiscalizacion automatica (CasoFiscalizacion.tipoCaso guarda el name()). Es parte del
// contrato del EJB remoto CasosAutomaticos: la fiscalizacion arma con ellos su jerarquia de situaciones.
public enum TipoCasoAutomatico {
    VIAJE_SIN_GUIA("Actividad del vehículo sin un viaje asignado ni en curso"),
    EXCESO_CARGA("El pesaje supera el peso del vehículo más su capacidad de carga declarada"),
    VEHICULO_SIN_HABILITACION("Actividad del vehículo con el permiso nacional de circulación o la ITV vencidos"),
    DESVIO_RUTA("La ruta reportada se aparta del recorrido entre el origen y el destino de la guía");

    private final String descripcion;

    TipoCasoAutomatico(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
