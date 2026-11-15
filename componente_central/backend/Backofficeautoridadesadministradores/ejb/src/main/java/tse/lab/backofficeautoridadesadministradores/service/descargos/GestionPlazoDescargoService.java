package tse.lab.backofficeautoridadesadministradores.service.descargos;

import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.*;
import java.util.logging.Logger;
import tse.lab.backofficeautoridadesadministradores.data.ConfiguracionDescargosDAO;
import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;
import tse.lab.model.ConfiguracionDescargos;

@Stateless
@DeclareRoles("ADMINISTRADOR")
@PermitAll
public class GestionPlazoDescargoService {
    private static final Logger LOG = Logger.getLogger(GestionPlazoDescargoService.class.getName());
    @EJB private ConfiguracionDescargosDAO configuracion;
    @Resource private SessionContext seguridad;

    public record PlazoDescargo(long revision, int plazoDescargoDias, String fechaActualizacion,
            String usuario, String fundamento) { }

    private void autorizar() {
        if (seguridad == null || !seguridad.isCallerInRole("ADMINISTRADOR"))
            throw new PlazoDescargoException(403, "Solo los administradores pueden configurar el plazo de descargo");
    }
    public PlazoDescargo obtener() {
        autorizar();
        return datos(configuracion.obtener(false));
    }
    public PlazoDescargo actualizar(long revisionBase, int dias, String fundamento, String correlationId) {
        autorizar();
        var c = configuracion.obtener(true);
        if (c.getRevision() != revisionBase)
            throw new PlazoDescargoException(409, "Otra persona actualizó el plazo. Recargá la configuración antes de guardar");
        int anterior = c.getPlazoDias();
        try { c.actualizar(dias, seguridad.getCallerPrincipal().getName(), fundamento); }
        catch (IllegalArgumentException e) { throw new PlazoDescargoException(400, e.getMessage()); }
        LOG.info(() -> LogBackoffice.evento("descargos.plazo_actualizado").correlationId(correlationId)
                .usuario(c.getUsuario()).campo("diasAnteriores", anterior).campo("dias", dias)
                .campo("revision", c.getRevision()).campo("fundamento", c.getFundamento()).toString());
        return datos(c);
    }
    private static PlazoDescargo datos(ConfiguracionDescargos c) {
        return new PlazoDescargo(c.getRevision(), c.getPlazoDias(), c.getActualizadaEn().toString(),
                c.getUsuario(), c.getFundamento());
    }
}
