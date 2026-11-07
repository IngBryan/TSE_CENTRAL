package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.time.Instant;
import java.util.List;
import jakarta.ejb.Remote;

/** Consulta interna EJB; no expone datos de Guías en una API pública. */
@Remote
public interface IntervalosGuiasGpsRemote {
    String LOOKUP = "ejb:frontofficeciudadanos/frontofficeciudadanos-ejb/IntervalosGuiasGpsService!"
            + "tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervalosGuiasGpsRemote";

    /** Intervalos candidatos solapados con [desde, hasta); puede devolver más de uno. */
    List<IntervaloGuiaGps> buscar(String matricula, Instant desde, Instant hasta);
}
