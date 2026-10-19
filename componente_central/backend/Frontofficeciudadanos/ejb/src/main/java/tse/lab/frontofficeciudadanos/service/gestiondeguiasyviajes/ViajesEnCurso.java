package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.util.List;

// Operaciones comunes a la vista local y la remota. EJB no permite que una misma interfaz sea @Local y
// @Remote del mismo bean, por eso cada vista la extiende.
public interface ViajesEnCurso {

    // Guias del vehiculo en estado ASIGNADA o EN_CURSO (lista vacia si no tiene o no existe)
    List<DatosViaje> buscarViajesEnCurso(String matricula);
}
