package tse.lab.frontofficeciudadanos.service.rutas;

import java.util.List;
import jakarta.ejb.Remote;

/** Contrato EJB remoto de solo lectura para Fiscalizacion y Monitoreo (sin depender de Next.js). */
@Remote
public interface RutasAutorizadasRemote {
    String LOOKUP = "ejb:frontofficeciudadanos/frontofficeciudadanos-ejb/RutasAutorizadasService!"
            + "tse.lab.frontofficeciudadanos.service.rutas.RutasAutorizadasRemote";

    List<DatosRutaAutorizada> obtenerPorGuia(String idGuia);

    /** Reconstruye las rutas evaluadas usando las huellas guardadas en la prueba del caso,
     * sin depender de las autorizaciones actualmente asociadas a la Guia.
     */
    List<DatosRutaAutorizada> obtenerPorHuellas(List<String> huellas);

}
