package tse.lab.frontofficeciudadanos.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.model.Empresa;
import tse.lab.model.Rubro;

// Perfil de la Empresa: el responsable consulta y actualiza los datos de su empresa (nombre publico,
// razon social y direccion principal). El numero de empresa se muestra pero no se modifica: es la
// clave con la que la referencian choferes, vehiculos, guias y el responsable, y lo asigna el MTOP.
// No exige que la empresa este habilitada: el responsable puede completar sus datos antes.
@Stateless
public class PerfilEmpresaService {

    private static final Logger LOG = Logger.getLogger(PerfilEmpresaService.class.getName());
    static final int MAX_NOMBRE = 100;
    static final int MAX_DIRECCION = 200;

    @EJB
    private AutorizacionResponsable autorizacion;

    // rubros: los nombres de los rubros de la empresa, ordenados. Los asigna el funcionario del MTOP: el
    // responsable los ve pero no los modifica (no estan en CambiosEmpresa).
    public record DatosEmpresa(Long nroEmpresa, String nombrePublico, String razonSocial, String direccion,
            boolean habilitada, List<String> rubros) {
        static DatosEmpresa de(Empresa e) {
            return new DatosEmpresa(e.getNroEmpresa(), e.getNombrePublico(), e.getRazonSocial(), e.getDireccion(),
                    e.isHabilitada(), e.getRubros().stream().map(Rubro::getNombre).toList());
        }
    }

    public record CambiosEmpresa(String nombrePublico, String razonSocial, String direccion) {
    }

    public DatosEmpresa obtener(String ciResponsable, String correlationId) throws NoAutorizadoException {
        return DatosEmpresa.de(autorizacion.empresaDelResponsable(ciResponsable, false, correlationId));
    }

    public DatosEmpresa actualizar(String ciResponsable, CambiosEmpresa cambios, String correlationId)
            throws NoAutorizadoException, DatosInvalidosException {
        Empresa empresa = autorizacion.empresaDelResponsable(ciResponsable, false, correlationId);

        String nombre = limpiar(cambios == null ? null : cambios.nombrePublico());
        String razon = limpiar(cambios == null ? null : cambios.razonSocial());
        String direccion = limpiar(cambios == null ? null : cambios.direccion());
        Map<String, String> errores = new LinkedHashMap<>();
        validar(errores, "nombrePublico", "El nombre público", "obligatorio", nombre, MAX_NOMBRE);
        validar(errores, "razonSocial", "La razón social", "obligatoria", razon, MAX_NOMBRE);
        validar(errores, "direccion", "La dirección principal", "obligatoria", direccion, MAX_DIRECCION);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        List<String> modificados = new ArrayList<>();
        if (!Objects.equals(nombre, empresa.getNombrePublico())) {
            modificados.add("nombrePublico");
        }
        if (!Objects.equals(razon, empresa.getRazonSocial())) {
            modificados.add("razonSocial");
        }
        if (!Objects.equals(direccion, empresa.getDireccion())) {
            modificados.add("direccion");
        }
        // Entidad administrada: los cambios se guardan al cerrar la transaccion
        empresa.setNombrePublico(nombre);
        empresa.setRazonSocial(razon);
        empresa.setDireccion(direccion);
        LOG.info(() -> LogCiudadanos.evento("empresa.actualizada").correlationId(correlationId)
                .usuario(ciResponsable).campo("nroEmpresa", empresa.getNroEmpresa()).campo("campos", modificados)
                .toString());
        return DatosEmpresa.de(empresa);
    }

    // Recorta espacios y colapsa espacios repetidos
    private static String limpiar(String valor) {
        return valor == null ? null : valor.trim().replaceAll("\\s+", " ");
    }

    private static void validar(Map<String, String> errores, String campo, String nombre, String obligatorio,
            String valor, int max) {
        if (valor == null || valor.isEmpty()) {
            errores.put(campo, nombre + " es " + obligatorio + ".");
        } else if (valor.length() > max) {
            errores.put(campo, nombre + " no puede tener más de " + max + " caracteres.");
        }
    }
}
