package tse.lab.backofficeautoridadesadministradores.service.reglas;

import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import tse.lab.backofficeautoridadesadministradores.data.ReglasDAO;
import tse.lab.model.RevisionReglas;
import tse.lab.reglas.ConfiguracionReglas;

@Stateless
@DeclareRoles("ADMINISTRADOR")
// La autorización programática en autorizar() permite devolver un 403 de negocio.
@PermitAll
public class GestionReglasService {
    @EJB private ReglasDAO reglas;
    @Resource private SessionContext seguridad;
    private void autorizar() {
        if (seguridad == null || !seguridad.isCallerInRole("ADMINISTRADOR"))
            throw new ReglasException(403, "Solo los administradores pueden configurar reglas");
    }
    public ConfiguracionReglas obtener() {
        autorizar(); return reglas.revision(reglas.vigente(false).getRevision()).datos();
    }
    public List<ConfiguracionReglas> historial() {
        autorizar(); return reglas.historial().stream().map(RevisionReglas::datos).toList();
    }
    public ConfiguracionReglas actualizar(long revisionBase, BigDecimal porcentaje, int kg, int metros,
            int segundos, String fundamento, String correlationId) {
        autorizar();
        if (fundamento == null || fundamento.isBlank() || fundamento.strip().length() > 500)
            throw new ReglasException(400, "El fundamento es obligatorio y admite hasta 500 caracteres");
        var vigente=reglas.vigente(true);
        if (vigente.getRevision() != revisionBase)
            throw new ReglasException(409, "Otra persona actualizó las reglas. Recargá la configuración antes de guardar");
        ConfiguracionReglas nueva;
        try {
            nueva=new ConfiguracionReglas(revisionBase+1, porcentaje, kg, metros, segundos,
                    Instant.now().toString(), seguridad.getCallerPrincipal().getName(), fundamento.strip(), false);
        } catch (IllegalArgumentException e) { throw new ReglasException(400, e.getMessage()); }
        reglas.guardar(new RevisionReglas(nueva, correlationId));
        vigente.avanzar(nueva.getRevision());
        // La revisión pendiente es el outbox: publicación fuera de esta transacción.
        return nueva;
    }
}
