package tse.lab.frontofficeciudadanos.service.rutas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.GuiaVista;
import tse.lab.model.Empresa;
import tse.lab.model.Guia;

/** Orquestador sin transaccion durante la llamada de red a OSRM. */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RutasGuiaService {
    @EJB private AutorizacionResponsable autorizacion;
    @EJB private GuiaDAO guias;
    @EJB private RutasAutorizadasService rutas;
    @EJB private PrevisualizacionRutasService planificador;
    @EJB private GeometriasService geometrias;
    @EJB private AsignacionRutasService asignacion;
    @EJB private GuiasService viajes;

    public List<DatosRutaAutorizada> consultar(String ciResponsable, String idGuia,
            String correlationId) throws NoAutorizadoException, RecursoNoEncontradoException {
        comprobarPertenencia(ciResponsable, idGuia, correlationId);
        return rutas.obtenerPorGuia(idGuia);
    }

    public GuiaVista asignarViaje(String ciResponsable, String idGuia, String matricula, Long idChofer,
            Long origenId, Long destinoId, String previsualizacionId, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        // No se modifican ni la guia ni sus asociaciones hasta terminar el calculo y revisar el resumen.
        viajes.opcionesAsignacion(ciResponsable, idGuia, correlationId);
        List<Long> ids = preparar(origenId, destinoId, previsualizacionId);
        return viajes.asignarConRutas(ciResponsable, idGuia, matricula, idChofer,
                origenId, destinoId, ids, correlationId);
    }

    public List<DatosRutaAutorizada> confirmar(String ciResponsable, String idGuia,
            Long origenId, Long destinoId, String previsualizacionId, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        comprobarPertenencia(ciResponsable, idGuia, correlationId);
        List<Long> ids = preparar(origenId, destinoId, previsualizacionId);
        asignacion.asociar(ciResponsable, idGuia, origenId, destinoId, ids, correlationId);
        return rutas.obtenerPorGuia(idGuia);
    }

    private List<Long> preparar(Long origenId, Long destinoId, String previsualizacionId) {
        if (previsualizacionId == null || !previsualizacionId.matches("[0-9a-f]{64}")) {
            throw new RutasException(400, "previsualizacion_requerida", "Debe previsualizar las rutas primero");
        }
        var preview = planificador.calcular(origenId, destinoId);
        if (!preview.previsualizacionId().equals(previsualizacionId)) {
            throw new RutasException(409, "rutas_actualizadas", "Las rutas cambiaron; previsualizalas nuevamente");
        }
        List<Long> ids = new ArrayList<>();
        for (var r : preview.rutas()) {
            try {
                ids.add(geometrias.obtenerOCrear(r.geoJson(), r.distanciaMetros()));
            } catch (RuntimeException conflicto) {
                // Si otra transaccion gano la insercion UNIQUE, consulta la geometria ya guardada.
                Long existente = geometrias.buscarId(r.sha256());
                if (existente == null) throw conflicto;
                ids.add(existente);
            }
        }
        return ids;
    }

    private void comprobarPertenencia(String ciResponsable, String idGuia, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        Guia guia = idGuia == null ? null : guias.buscarPorId(idGuia);
        if (guia == null || guia.getEmpresa() == null
                || !Objects.equals(guia.getEmpresa().getNroEmpresa(), empresa.getNroEmpresa())) {
            throw new RecursoNoEncontradoException("La Guia no pertenece a la empresa");
        }
    }
}
