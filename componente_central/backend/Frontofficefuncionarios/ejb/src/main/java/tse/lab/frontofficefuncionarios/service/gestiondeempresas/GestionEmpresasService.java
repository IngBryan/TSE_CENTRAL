package tse.lab.frontofficefuncionarios.service.gestiondeempresas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficefuncionarios.data.CiudadanoDAO;
import tse.lab.frontofficefuncionarios.data.EmpresaDAO;
import tse.lab.frontofficefuncionarios.data.ResponsableDAO;
import tse.lab.frontofficefuncionarios.service.ConflictoException;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.Responsable;

// CU Gestionar empresas de transporte (funcionario): listado, alta, consulta, modificacion y asociacion
// de un usuario (ciudadano) como responsable de la empresa. Cada operacion verifica primero que quien
// llama sea funcionario (FA5): si no, se rechaza sin leer ni modificar nada. Las consultas no modifican
// el estado; las altas y modificaciones validan todos los datos antes de tocar la base.
@Stateless
public class GestionEmpresasService {

    private static final Logger LOG = Logger.getLogger(GestionEmpresasService.class.getName());

    // Mismos limites que el perfil de la empresa en Frontofficeciudadanos
    static final int MAX_NOMBRE = 100;
    static final int MAX_DIRECCION = 200;
    private static final Pattern CEDULA = Pattern.compile("\\d{6,8}");
    static final int MAX_DISPONIBLES = 50;
    // Una empresa sin responsable no se puede habilitar (ni en el alta ni al modificarla)
    static final String SIN_RESPONSABLE_NO_SE_HABILITA =
            "Para habilitar la empresa tiene que tener un responsable asociado.";

    @EJB
    private AutorizacionFuncionario autorizacion;
    @EJB
    private EmpresaDAO empresas;
    @EJB
    private CiudadanoDAO ciudadanos;
    @EJB
    private ResponsableDAO responsables;

    public record EmpresaResumen(Long nroEmpresa, String nombrePublico, String razonSocial, boolean habilitada,
            String ciResponsable) {
    }

    // Solo el primer nombre y primer apellido del responsable (Ley 18.331), y su estado frente a la PDI
    public record ResponsableVista(String ci, String primerNombre, String primerApellido, String estado) {
    }

    public record EmpresaDetalle(Long nroEmpresa, String nombrePublico, String razonSocial, String direccion,
            boolean habilitada, ResponsableVista responsable) {
    }

    // habilitada es opcional: en el alta, por defecto la empresa queda no habilitada. ciResponsable es
    // opcional: la cedula de un usuario registrado y validado (ver usuariosDisponibles) que queda como
    // responsable de la empresa en la misma transaccion.
    public record AltaEmpresa(Long nroEmpresa, String nombrePublico, String razonSocial, String direccion,
            Boolean habilitada, String ciResponsable) {
    }

    // Un usuario que se puede asociar como responsable: validado y sin ser responsable de otra empresa.
    // Solo primer nombre y primer apellido (Ley 18.331).
    public record UsuarioDisponible(String ci, String primerNombre, String primerApellido) {
    }

    // El nroEmpresa no se modifica: es la clave con la que la referencian choferes, vehiculos, guias y el
    // responsable. habilitada es opcional: si no viene, se conserva.
    public record CambiosEmpresa(String nombrePublico, String razonSocial, String direccion, Boolean habilitada) {
    }

    public record AsociacionUsuario(String ci) {
    }

    public record OperacionRealizada(String mensaje, EmpresaDetalle empresa) {
    }

    public List<EmpresaResumen> listar(String ci, String correlationId) throws NoAutorizadoException {
        autorizacion.funcionario(ci, correlationId);
        List<EmpresaResumen> lista = empresas.listar().stream().map(GestionEmpresasService::resumen).toList();
        LOG.info(() -> "evento=empresas.listado correlationId=" + correlationId + " usuario=" + ci + " cantidad="
                + lista.size());
        return lista;
    }

    // FA2: consulta de una empresa
    public EmpresaDetalle detalle(String ci, Long nroEmpresa, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        Empresa empresa = empresa(nroEmpresa, ci, correlationId);
        LOG.info(() -> "evento=empresa.consulta correlationId=" + correlationId + " usuario=" + ci + " nroEmpresa="
                + nroEmpresa);
        return detalle(empresa);
    }

    // FA4, paso 2: los usuarios que se pueden elegir como responsable. filtroCi (opcional) es el comienzo
    // de la cedula; se devuelven hasta MAX_DISPONIBLES, ordenados por cedula.
    public List<UsuarioDisponible> usuariosDisponibles(String ci, String filtroCi, String correlationId)
            throws NoAutorizadoException, DatosInvalidosException {
        autorizacion.funcionario(ci, correlationId);
        String prefijo = filtroCi == null ? "" : filtroCi.trim();
        if (!prefijo.matches("\\d{0,8}")) {
            throw new DatosInvalidosException(
                    Map.of("ci", "El filtro tiene que ser el comienzo de una cédula (solo dígitos)."));
        }
        List<UsuarioDisponible> lista = ciudadanos.disponiblesComoResponsable(prefijo, MAX_DISPONIBLES).stream()
                .map(c -> new UsuarioDisponible(c.getCi(), c.getPrimerNombre(), c.getPrimerApellido()))
                .toList();
        LOG.info(() -> "evento=empresas.usuarios_disponibles correlationId=" + correlationId + " usuario=" + ci
                + " filtro=" + prefijo + " cantidad=" + lista.size());
        return lista;
    }

    // FA1: alta, opcionalmente con su responsable. 409 EMPRESA_EXISTENTE si ya hay una empresa con ese
    // numero. Si viene ciResponsable se controla igual que en la asociacion (FA4) antes de registrar nada:
    // si el usuario no se puede asociar, la empresa tampoco se crea.
    public OperacionRealizada alta(String ci, AltaEmpresa datos, String correlationId)
            throws NoAutorizadoException, DatosInvalidosException, ConflictoException, RecursoNoEncontradoException {
        autorizacion.funcionario(ci, correlationId);
        Long nro = datos == null ? null : datos.nroEmpresa();
        String nombre = limpiar(datos == null ? null : datos.nombrePublico());
        String razon = limpiar(datos == null ? null : datos.razonSocial());
        String direccion = limpiar(datos == null ? null : datos.direccion());
        String ciResponsable = datos == null || datos.ciResponsable() == null ? "" : datos.ciResponsable().trim();
        Map<String, String> errores = new LinkedHashMap<>();
        if (nro == null || nro < 1) {
            errores.put("nroEmpresa", "El número de empresa es obligatorio y tiene que ser un entero positivo.");
        }
        validarTextos(errores, nombre, razon, direccion);
        if (!ciResponsable.isEmpty() && !CEDULA.matcher(ciResponsable).matches()) {
            errores.put("ciResponsable", "La cédula tiene que tener de 6 a 8 dígitos.");
        }
        if (Boolean.TRUE.equals(datos == null ? null : datos.habilitada()) && ciResponsable.isEmpty()) {
            errores.put("habilitada", SIN_RESPONSABLE_NO_SE_HABILITA);
        }
        if (!errores.isEmpty()) {
            LOG.warning(() -> "evento=empresa.alta.rechazada correlationId=" + correlationId + " usuario=" + ci
                    + " motivo=datos_invalidos campos=" + errores.keySet());
            throw new DatosInvalidosException(errores);
        }
        if (empresas.buscarPorId(nro) != null) {
            throw empresaExistente(nro, ci, correlationId);
        }
        Ciudadano ciudadano = ciResponsable.isEmpty() ? null
                : candidatoResponsable(ciResponsable, nro, ci, correlationId);

        Empresa empresa = new Empresa(nro, nombre, razon, direccion, null);
        empresa.setHabilitada(Boolean.TRUE.equals(datos.habilitada()));
        // Otra alta con el mismo numero pudo entrar despues del control de arriba: la PK la rechaza aca, antes
        // de registrar al responsable, y se responde 409 en vez de fallar al cerrar la transaccion
        if (!empresas.guardarNueva(empresa)) {
            throw empresaExistente(nro, ci, correlationId);
        }
        if (ciudadano != null) {
            Responsable responsable = new Responsable(ciudadano, empresa);
            responsables.guardar(responsable);
            empresa.setResponsable(responsable);
        }
        LOG.info(() -> "evento=empresa.alta correlationId=" + correlationId + " usuario=" + ci + " nroEmpresa=" + nro
                + " habilitada=" + empresa.isHabilitada() + " ciResponsable="
                + (ciudadano == null ? "-" : ciudadano.getCi()));
        return new OperacionRealizada("La empresa fue registrada.", detalle(empresa));
    }

    // FA3: modificacion de los datos de la empresa (no de su numero)
    public OperacionRealizada modificar(String ci, Long nroEmpresa, CambiosEmpresa cambios, String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, DatosInvalidosException {
        autorizacion.funcionario(ci, correlationId);
        Empresa empresa = empresa(nroEmpresa, ci, correlationId);
        String nombre = limpiar(cambios == null ? null : cambios.nombrePublico());
        String razon = limpiar(cambios == null ? null : cambios.razonSocial());
        String direccion = limpiar(cambios == null ? null : cambios.direccion());
        Boolean habilitada = cambios == null ? null : cambios.habilitada();
        Map<String, String> errores = new LinkedHashMap<>();
        validarTextos(errores, nombre, razon, direccion);
        // Solo se controla al pasar a habilitada: una que ya lo estaba se puede seguir editando
        if (Boolean.TRUE.equals(habilitada) && !empresa.isHabilitada() && empresa.getResponsable() == null) {
            errores.put("habilitada", SIN_RESPONSABLE_NO_SE_HABILITA);
        }
        if (!errores.isEmpty()) {
            LOG.warning(() -> "evento=empresa.modificacion.rechazada correlationId=" + correlationId + " usuario="
                    + ci + " nroEmpresa=" + nroEmpresa + " motivo=datos_invalidos campos=" + errores.keySet());
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
        if (habilitada != null && habilitada != empresa.isHabilitada()) {
            modificados.add("habilitada");
        }
        // Entidad administrada: los cambios se guardan al cerrar la transaccion
        empresa.setNombrePublico(nombre);
        empresa.setRazonSocial(razon);
        empresa.setDireccion(direccion);
        if (habilitada != null) {
            empresa.setHabilitada(habilitada);
        }
        LOG.info(() -> "evento=empresa.modificada correlationId=" + correlationId + " usuario=" + ci + " nroEmpresa="
                + nroEmpresa + " campos=" + modificados);
        return new OperacionRealizada("La empresa fue modificada.", detalle(empresa));
    }

    // FA4: asocia un usuario ya registrado (ciudadano, por cedula) a la empresa como su responsable. Este
    // modulo no crea ciudadanos: los registra y valida contra la PDI Frontofficeciudadanos. Repetir la misma
    // asociacion no cambia nada. 404 USUARIO_NO_ENCONTRADO si la cedula no esta registrada; 409
    // USUARIO_NO_VALIDADO si no esta VALIDADO, EMPRESA_CON_RESPONSABLE si la empresa ya tiene otro
    // responsable y RESPONSABLE_DE_OTRA_EMPRESA si el usuario ya es responsable de otra (el modelo admite
    // uno por empresa y una empresa por usuario).
    public OperacionRealizada asociarUsuario(String ci, Long nroEmpresa, AsociacionUsuario datos,
            String correlationId)
            throws NoAutorizadoException, RecursoNoEncontradoException, DatosInvalidosException, ConflictoException {
        autorizacion.funcionario(ci, correlationId);
        String ciUsuario = datos == null || datos.ci() == null ? "" : datos.ci().trim();
        if (!CEDULA.matcher(ciUsuario).matches()) {
            throw new DatosInvalidosException(Map.of("ci", "La cédula tiene que tener de 6 a 8 dígitos."));
        }
        Empresa empresa = nroEmpresa == null ? null : empresas.buscarPorIdParaActualizar(nroEmpresa);
        if (empresa == null) {
            throw noEncontrada(nroEmpresa, ci, correlationId);
        }

        Responsable actual = empresa.getResponsable();
        if (actual != null && actual.getCi().equals(ciUsuario)) {
            LOG.info(() -> "evento=empresa.usuario_asociado correlationId=" + correlationId + " usuario=" + ci
                    + " nroEmpresa=" + nroEmpresa + " ciUsuario=" + ciUsuario + " resultado=ya_asociado");
            return new OperacionRealizada("El usuario ya estaba asociado a la empresa.", detalle(empresa));
        }
        if (actual != null) {
            throw rechazoAsociacion(ci, nroEmpresa, ciUsuario, correlationId, "EMPRESA_CON_RESPONSABLE",
                    "La empresa ya tiene un responsable asociado.");
        }
        Ciudadano ciudadano = candidatoResponsable(ciUsuario, nroEmpresa, ci, correlationId);

        Responsable responsable = new Responsable(ciudadano, empresa);
        responsables.guardar(responsable);
        empresa.setResponsable(responsable);
        LOG.info(() -> "evento=empresa.usuario_asociado correlationId=" + correlationId + " usuario=" + ci
                + " nroEmpresa=" + nroEmpresa + " ciUsuario=" + ciUsuario);
        return new OperacionRealizada("El usuario fue asociado a la empresa.", detalle(empresa));
    }

    // El ciudadano que puede quedar como responsable (alta y asociacion): registrado, validado y sin ser
    // responsable de otra empresa. Si no, 404 USUARIO_NO_ENCONTRADO o 409 RESPONSABLE_DE_OTRA_EMPRESA /
    // USUARIO_NO_VALIDADO. Su fila queda bloqueada hasta el fin de la transaccion: si dos funcionarios lo
    // asocian a la vez, el segundo espera y despues ve al responsable que registro el primero (409).
    private Ciudadano candidatoResponsable(String ciUsuario, Long nroEmpresa, String ci, String correlationId)
            throws RecursoNoEncontradoException, ConflictoException {
        Ciudadano ciudadano = ciudadanos.buscarPorCiParaActualizar(ciUsuario);
        if (ciudadano == null) {
            LOG.info(() -> "evento=empresa.asociacion.rechazada correlationId=" + correlationId + " usuario=" + ci
                    + " nroEmpresa=" + nroEmpresa + " ciUsuario=" + ciUsuario + " motivo=USUARIO_NO_ENCONTRADO");
            throw new RecursoNoEncontradoException("USUARIO_NO_ENCONTRADO",
                    "No hay un usuario registrado con esa cédula.");
        }
        Responsable deOtra = responsables.buscarPorCi(ciUsuario);
        if (deOtra != null) {
            throw rechazoAsociacion(ci, nroEmpresa, ciUsuario, correlationId, "RESPONSABLE_DE_OTRA_EMPRESA",
                    "El usuario ya es responsable de la empresa " + deOtra.getEmpresa().getNroEmpresa() + ".");
        }
        if (ciudadano.getEstado() != EstadoValidacion.VALIDADO) {
            throw rechazoAsociacion(ci, nroEmpresa, ciUsuario, correlationId, "USUARIO_NO_VALIDADO",
                    "El usuario no está validado (" + ciudadano.getEstado() + ").");
        }
        return ciudadano;
    }

    private Empresa empresa(Long nroEmpresa, String ci, String correlationId) throws RecursoNoEncontradoException {
        Empresa empresa = nroEmpresa == null ? null : empresas.buscarPorId(nroEmpresa);
        if (empresa == null) {
            throw noEncontrada(nroEmpresa, ci, correlationId);
        }
        return empresa;
    }

    private static RecursoNoEncontradoException noEncontrada(Long nroEmpresa, String ci, String correlationId) {
        LOG.info(() -> "evento=empresa.no_encontrada correlationId=" + correlationId + " usuario=" + ci
                + " nroEmpresa=" + nroEmpresa);
        return new RecursoNoEncontradoException("EMPRESA_NO_ENCONTRADA", "La empresa no existe.");
    }

    private static ConflictoException empresaExistente(Long nro, String ci, String correlationId) {
        LOG.warning(() -> "evento=empresa.alta.rechazada correlationId=" + correlationId + " usuario=" + ci
                + " nroEmpresa=" + nro + " motivo=EMPRESA_EXISTENTE");
        return new ConflictoException("EMPRESA_EXISTENTE", "Ya existe una empresa con el número " + nro + ".");
    }

    private static ConflictoException rechazoAsociacion(String ci, Long nroEmpresa, String ciUsuario,
            String correlationId, String codigo, String mensaje) {
        LOG.warning(() -> "evento=empresa.asociacion.rechazada correlationId=" + correlationId + " usuario=" + ci
                + " nroEmpresa=" + nroEmpresa + " ciUsuario=" + ciUsuario + " motivo=" + codigo);
        return new ConflictoException(codigo, mensaje);
    }

    private static EmpresaResumen resumen(Empresa e) {
        Responsable r = e.getResponsable();
        return new EmpresaResumen(e.getNroEmpresa(), e.getNombrePublico(), e.getRazonSocial(), e.isHabilitada(),
                r == null ? null : r.getCi());
    }

    private static EmpresaDetalle detalle(Empresa e) {
        Responsable r = e.getResponsable();
        ResponsableVista responsable = null;
        if (r != null) {
            Ciudadano c = r.getCiudadano();
            responsable = new ResponsableVista(r.getCi(), c == null ? null : c.getPrimerNombre(),
                    c == null ? null : c.getPrimerApellido(),
                    c == null || c.getEstado() == null ? null : c.getEstado().name());
        }
        return new EmpresaDetalle(e.getNroEmpresa(), e.getNombrePublico(), e.getRazonSocial(), e.getDireccion(),
                e.isHabilitada(), responsable);
    }

    private static void validarTextos(Map<String, String> errores, String nombre, String razon, String direccion) {
        validar(errores, "nombrePublico", "El nombre público", "obligatorio", nombre, MAX_NOMBRE);
        validar(errores, "razonSocial", "La razón social", "obligatoria", razon, MAX_NOMBRE);
        validar(errores, "direccion", "La dirección", "obligatoria", direccion, MAX_DIRECCION);
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
