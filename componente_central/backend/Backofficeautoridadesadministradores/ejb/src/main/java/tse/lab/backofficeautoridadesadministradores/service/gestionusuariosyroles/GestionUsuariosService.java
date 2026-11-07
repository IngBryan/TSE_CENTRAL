package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioDAO;
import tse.lab.model.Ciudadano;
import tse.lab.model.Funcionario;

/**
 * Listado de los usuarios de carga.uy: funcionarios, responsables, choferes,
 * administradores y autoridades. Un ciudadano sin ninguno de esos roles no
 * aparece.
 *
 * Cada rol está en su propia tabla y una misma cédula puede estar en varias,
 * así que el listado se arma por persona: una fila por cédula, con todos sus
 * roles.
 */
@Stateless
public class GestionUsuariosService {

    static final int TAMAÑO_POR_DEFECTO = 20;
    static final int TAMAÑO_MAXIMO = 100;
    static final int LARGO_MAXIMO_CI = 8;
    private static final Pattern CI_PARCIAL = Pattern.compile("\\d{1," + LARGO_MAXIMO_CI + "}");

    @EJB
    private UsuarioDAO usuarios;

    /**
     * Obtiene una página de usuarios, ordenados por cédula. Todos los
     * parámetros son opcionales.
     *
     * @param rol solo los usuarios con este rol (se muestran con todos sus roles)
     * @param ci solo las cédulas que empiezan así; acepta puntos y guion
     * @throws PaginaInvalidaException si algún parámetro no es válido
     */
    public PaginaUsuarios listar(String pagina, String tamaño, String rol, String ci) {
        RolUsuario filtroRol = rol(rol);
        String prefijoCi = prefijoCi(ci);

        Integer nroPagina = entero(pagina, 1);
        if (nroPagina == null || nroPagina < 1) {
            throw new PaginaInvalidaException(
                    "La página tiene que ser un número entero mayor o igual a 1");
        }

        Integer cantidad = entero(tamaño, TAMAÑO_POR_DEFECTO);
        if (cantidad == null || cantidad < 1 || cantidad > TAMAÑO_MAXIMO) {
            throw new PaginaInvalidaException(
                    "El tamaño de página tiene que ser un número entero entre 1 y " + TAMAÑO_MAXIMO);
        }

        long total = usuarios.contarUsuarios(filtroRol, prefijoCi);
        int totalPaginas = (int) ((total + cantidad - 1) / cantidad);
        // long: con paginas muy altas (nroPagina - 1) * cantidad desborda un int
        long desde = (long) (nroPagina - 1) * cantidad;
        List<DatosUsuario> lista = desde >= total
                ? List.of()
                : datosDe(usuarios.listarCedulas(filtroRol, prefijoCi, (int) desde, cantidad));

        return new PaginaUsuarios(lista, nroPagina, cantidad, total, totalPaginas);
    }

    private List<DatosUsuario> datosDe(List<String> cedulas) {
        // Se pudieron borrar usuarios entre el conteo y la consulta: IN () no es SQL
        // valido
        if (cedulas.isEmpty()) {
            return List.of();
        }

        // Conserva el orden de la página
        Map<String, FilaUsuario> filas = new LinkedHashMap<>();
        cedulas.forEach(ci -> filas.put(ci, new FilaUsuario()));

        for (Ciudadano ciudadano : usuarios.buscarCiudadanos(cedulas)) {
            FilaUsuario fila = filas.get(ciudadano.getCi());
            fila.nombres = ciudadano.getNombres();
            fila.apellidos = ciudadano.getApellidos();
            fila.email = ciudadano.getEmail();
        }

        // El email del ciudadano tiene prioridad: el de funcionario completa si no hay
        // otro
        for (Funcionario funcionario : usuarios.buscarFuncionarios(cedulas)) {
            FilaUsuario fila = filas.get(funcionario.getCi());
            fila.roles.add(RolUsuario.FUNCIONARIO);
            if (fila.email == null) {
                fila.email = funcionario.getEmail();
            }
        }

        agregarRol(filas, usuarios.filtrarResponsables(cedulas), RolUsuario.RESPONSABLE);
        agregarRol(filas, usuarios.filtrarChoferes(cedulas), RolUsuario.CHOFER);
        agregarRol(filas, usuarios.filtrarAdministradores(cedulas), RolUsuario.ADMINISTRADOR);
        agregarRol(filas, usuarios.filtrarAutoridades(cedulas), RolUsuario.AUTORIDAD);

        return filas.entrySet().stream()
                .map(entrada -> entrada.getValue().aDatos(entrada.getKey()))
                .toList();
    }

    private static void agregarRol(
            Map<String, FilaUsuario> filas,
            List<String> cedulas,
            RolUsuario rol) {

        cedulas.forEach(ci -> filas.get(ci).roles.add(rol));
    }

    // null si no vino
    private static RolUsuario rol(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return RolUsuario.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PaginaInvalidaException("El rol tiene que ser uno de "
                    + Arrays.toString(RolUsuario.values()));
        }
    }

    // null si no vino. 5.407.438-0 busca 54074380
    private static String prefijoCi(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String digitos = ValidacionUsuario.sinSeparadores(valor);
        if (!CI_PARCIAL.matcher(digitos).matches()) {
            throw new PaginaInvalidaException(
                    "La cédula tiene que tener solo dígitos, hasta " + LARGO_MAXIMO_CI);
        }
        return digitos;
    }

    // null si no es un entero; porDefecto si no vino
    private static Integer entero(String valor, int porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return Integer.valueOf(valor.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class FilaUsuario {
        private final Set<RolUsuario> roles = EnumSet.noneOf(RolUsuario.class);
        private String nombres;
        private String apellidos;
        private String email;

        private DatosUsuario aDatos(String ci) {
            return new DatosUsuario(ci, nombres, apellidos, email, List.copyOf(roles));
        }
    }
}
