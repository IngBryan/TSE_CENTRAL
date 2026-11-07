package tse.lab.frontofficeciudadanos.service.rutas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.frontofficeciudadanos.data.RutaReferenciaDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.model.Empresa;
import tse.lab.model.Guia;
import tse.lab.model.Localidad;
import tse.lab.model.RutaReferencia;

/** Frontera transaccional: verifica responsable, pertenencia, localidades y estado bajo lock. */
@Stateless
public class AsignacionRutasService {
    @EJB private AutorizacionResponsable autorizacion;
    @EJB private GuiaDAO guias;
    @EJB private LocalidadDAO localidades;
    @EJB private RutaReferenciaDAO rutas;

    public void asociar(String ciResponsable, String idGuia,
            Long origenId, Long destinoId, List<Long> ids, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = guias.buscarPorIdParaActualizar(idGuia);
        if (guia == null || guia.getEmpresa() == null
                || !Objects.equals(guia.getEmpresa().getNroEmpresa(), empresa.getNroEmpresa())) {
            throw new RecursoNoEncontradoException("La Guia no pertenece a la empresa");
        }
        Localidad origen = localidades.buscarPorId(origenId);
        Localidad destino = localidades.buscarPorId(destinoId);
        if (origen == null || destino == null
                || !corresponde(guia.getOrigenLocalidad(), guia.getOrigen(), origen)
                || !corresponde(guia.getDestinoLocalidad(), guia.getDestino(), destino)) {
            throw new RutasException(409, "origen_destino_desactualizado",
                    "Las localidades no coinciden con el origen y destino actuales de la Guia");
        }
        List<RutaReferencia> referencias = new ArrayList<>();
        for (Long id : ids) {
            RutaReferencia ruta = rutas.buscarPorId(id);
            if (ruta == null) {
                throw new RutasException(409, "geometria_no_encontrada", "No se encontro una geometria calculada");
            }
            referencias.add(ruta);
        }
        try {
            guia.reemplazarRutasAutorizadas(referencias);
        } catch (IllegalStateException e) {
            throw new RutasException(409, "viaje_no_modificable", e.getMessage());
        }
        // La transaccion persiste las asociaciones por cascade y bloquea cambios concurrentes de la Guia.

    }

    // Las guias nuevas se comparan por identidad; las historicas solo por nombre unico en catalogo.
    private boolean corresponde(Localidad asociada, String nombreHistorico, Localidad seleccionada) {
        if (asociada != null) return Objects.equals(asociada.getId(), seleccionada.getId());
        Localidad unica = localidades.buscarUnicaPorNombre(nombreHistorico);
        return unica != null && Objects.equals(unica.getId(), seleccionada.getId());
    }

}
