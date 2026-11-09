package tse.lab.frontofficeciudadanos.service.rutas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.GuiaVista;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.NuevaGuia;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.EdicionGuia;
import tse.lab.model.Empresa;
import tse.lab.model.Guia;
import tse.lab.model.Localidad;

/** Orquestador sin transaccion durante la llamada de red a OSRM. */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RutasGuiaService {
    @EJB private AutorizacionResponsable autorizacion;
    @EJB private GuiaDAO guias;
    @EJB private LocalidadDAO localidades;
    @EJB private RutasAutorizadasService rutas;
    @EJB private PrevisualizacionRutasService planificador;
    @EJB private GeometriasService geometrias;
    @EJB private AsignacionRutasService asignacion;
    @EJB private GuiasService viajes;

    public GuiaVista crearGuia(String ciResponsable, NuevaGuia datos, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, DatosInvalidosException {
        autorizacion.empresaDelResponsable(ciResponsable, true, correlationId);
        if (datos == null) return viajes.crear(ciResponsable, null, correlationId);
        List<Long> ids = preparar(datos.origenId(), datos.destinoId(), datos.previsualizacionId());
        return viajes.crearConRuta(ciResponsable, datos, ids, correlationId);
    }

    public GuiaVista editarGuia(String ciResponsable, String idGuia, EdicionGuia datos, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException, DatosInvalidosException {
        comprobarPertenencia(ciResponsable, idGuia, correlationId);
        Guia actual = guias.buscarPorId(idGuia);
        // La ausencia de ruta no bloquea cambios de carga, fecha o rubro.
        // Si cambia el par de localidades, la nueva ruta se valida antes de modificar la guia.
        boolean cambia = datos != null && (
                !mismaLocalidad(actual.getOrigenLocalidad(), actual.getOrigen(), datos.origenId())
                || !mismaLocalidad(actual.getDestinoLocalidad(), actual.getDestino(), datos.destinoId())
                || (datos.origen() != null && !Objects.equals(actual.getOrigen(), datos.origen()))
                || (datos.destino() != null && !Objects.equals(actual.getDestino(), datos.destino())));
        List<Long> ids = cambia ? preparar(datos.origenId(), datos.destinoId(), datos.previsualizacionId()) : null;
        return viajes.editarConRuta(ciResponsable, idGuia, datos, ids, correlationId);
    }

    private boolean mismaLocalidad(Localidad actual, String nombre, Long id) {
        if (id == null) return true; // El servicio de guias valida los nombres de clientes anteriores.
        Localidad anterior = actual == null ? localidades.buscarUnicaPorNombre(nombre) : actual;
        return anterior != null && Objects.equals(anterior.getId(), id);
    }

    public List<DatosRutaAutorizada> consultar(String ciResponsable, String idGuia,
            String correlationId) throws NoAutorizadoException, RecursoNoEncontradoException {
        comprobarPertenencia(ciResponsable, idGuia, correlationId);
        return rutas.obtenerPorGuia(idGuia);
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
