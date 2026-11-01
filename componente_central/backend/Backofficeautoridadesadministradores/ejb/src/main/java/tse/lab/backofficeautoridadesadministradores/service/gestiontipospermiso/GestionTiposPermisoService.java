package tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso;

import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import tse.lab.backofficeautoridadesadministradores.data.TipoPermisoDAO;
import tse.lab.model.TipoPermiso;

/**
 * Administra los tipos de permiso a demanda. El Permiso Nacional de
 * Circulación y la ITV son fijos y no pasan por este catálogo.
 *
 * No hay baja física: desactivar un tipo deja de exigirlo, pero
 * conserva lo que las empresas ya cargaron.
 */
@Stateless
public class GestionTiposPermisoService {

    static final int MAX_NOMBRE = 100;

    @EJB
    private TipoPermisoDAO tipos;

    public record DatosTipoPermiso(
            Long id,
            String nombre,
            boolean pideNumero,
            boolean pideVencimiento,
            boolean activo) {
    }

    public List<DatosTipoPermiso> listarTodos() {
        return tipos.listarTodos()
                .stream()
                .map(GestionTiposPermisoService::datos)
                .toList();
    }

    public DatosTipoPermiso registrar(
            String nombre,
            boolean pideNumero,
            boolean pideVencimiento) {

        String normalizado = validarNombre(nombre, null);
        TipoPermiso tipo =
                new TipoPermiso(normalizado, pideNumero, pideVencimiento);

        tipos.guardar(tipo);

        return datos(tipo);
    }

    public DatosTipoPermiso modificar(
            Long id,
            String nombre,
            boolean pideNumero,
            boolean pideVencimiento,
            boolean activo) {

        TipoPermiso tipo = id == null ? null : tipos.buscarPorId(id);

        if (tipo == null) {
            throw new TipoPermisoNoEncontradoException(
                    "El tipo de permiso no existe");
        }

        tipo.setNombre(validarNombre(nombre, id));
        tipo.setPideNumero(pideNumero);
        tipo.setPideVencimiento(pideVencimiento);
        tipo.setActivo(activo);

        return datos(tipo);
    }

    private String validarNombre(String nombre, Long idExcluido) {
        String normalizado = nombre == null
                ? ""
                : nombre.trim().replaceAll("\\s+", " ");

        if (normalizado.isEmpty()) {
            throw new TipoPermisoInvalidoException(
                    "El nombre es obligatorio");
        }

        if (normalizado.length() > MAX_NOMBRE) {
            throw new TipoPermisoInvalidoException(
                    "El nombre supera los " + MAX_NOMBRE + " caracteres");
        }

        if (tipos.existeNombre(normalizado, idExcluido)) {
            throw new TipoPermisoDuplicadoException(
                    "Ya existe un tipo de permiso con ese nombre");
        }

        return normalizado;
    }

    private static DatosTipoPermiso datos(TipoPermiso t) {
        return new DatosTipoPermiso(
                t.getId(),
                t.getNombre(),
                t.isPideNumero(),
                t.isPideVencimiento(),
                t.isActivo());
    }
}
