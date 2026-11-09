package tse.lab.backofficeautoridadesadministradores.service.reglas;

import jakarta.ejb.*;
import java.util.logging.Logger;
import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;

@Singleton
@Startup
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PublicadorReglas {
    @EJB private EnvioReglasService envio;
    private static final Logger LOG=Logger.getLogger(PublicadorReglas.class.getName());
    @Schedule(hour="*", minute="*", second="*/5", persistent=false)
    public void publicar() {
        try {
            for (int i=0; i<10 && envio.publicarSiguiente(); i++) { /* lote acotado */ }
        } catch (Exception e) {
            LogBackoffice.evento("reglas.publicacion_pendiente").errorSinPila(e).escribirEn(LOG);
        }
    }
}
