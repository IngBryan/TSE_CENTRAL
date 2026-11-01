package tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.backofficeautoridadesadministradores.data.TipoPermisoDAO;
import tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso.GestionTiposPermisoService.DatosTipoPermiso;
import tse.lab.model.TipoPermiso;

class GestionTiposPermisoServiceTest {

    // DAO en memoria; el id se asigna por reflexion, como lo haria la base
    private static class TipoPermisoDAOFalso extends TipoPermisoDAO {
        private final List<TipoPermiso> tipos = new ArrayList<>();
        private long proximoId = 1;

        @Override
        public void guardar(TipoPermiso tipo) {
            try {
                Field id = TipoPermiso.class.getDeclaredField("id");
                id.setAccessible(true);
                id.set(tipo, proximoId++);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            tipos.add(tipo);
        }

        @Override
        public TipoPermiso buscarPorId(Long id) {
            return tipos.stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);
        }

        @Override
        public List<TipoPermiso> listarTodos() {
            return tipos.stream().sorted(Comparator.comparing(TipoPermiso::getNombre)).toList();
        }

        @Override
        public boolean existeNombre(String nombre, Long idExcluido) {
            return tipos.stream().anyMatch(t -> t.getNombre().equalsIgnoreCase(nombre)
                    && !Objects.equals(t.getId(), idExcluido));
        }
    }

    private GestionTiposPermisoService servicio;

    @BeforeEach
    void setUp() throws Exception {
        servicio = new GestionTiposPermisoService();
        Field campo = GestionTiposPermisoService.class.getDeclaredField("tipos");
        campo.setAccessible(true);
        campo.set(servicio, new TipoPermisoDAOFalso());
    }

    @Test
    void registraUnTipoActivoConElNombreNormalizado() {
        DatosTipoPermiso t = servicio.registrar("  Mercaderia   peligrosa ", true, true);
        assertEquals("Mercaderia peligrosa", t.nombre());
        assertTrue(t.activo());
        assertEquals(List.of(t), servicio.listarTodos());
    }

    @Test
    void rechazaNombreVacioLargoODuplicado() {
        servicio.registrar("Refrigerado", false, true);
        assertThrows(TipoPermisoInvalidoException.class, () -> servicio.registrar(" ", true, true));
        assertThrows(TipoPermisoInvalidoException.class,
                () -> servicio.registrar("x".repeat(GestionTiposPermisoService.MAX_NOMBRE + 1), true, true));
        assertThrows(TipoPermisoDuplicadoException.class, () -> servicio.registrar("REFRIGERADO", true, true));
    }

    @Test
    void modificaYDesactivaSinChocarConSuPropioNombre() {
        DatosTipoPermiso t = servicio.registrar("Refrigerado", false, true);
        DatosTipoPermiso r = servicio.modificar(t.id(), "Refrigerado", true, false, false);
        assertTrue(r.pideNumero());
        assertFalse(r.pideVencimiento());
        assertFalse(r.activo());
    }

    @Test
    void noModificaUnTipoInexistenteNiLePoneElNombreDeOtro() {
        DatosTipoPermiso a = servicio.registrar("A", true, true);
        servicio.registrar("B", true, true);
        assertThrows(TipoPermisoNoEncontradoException.class, () -> servicio.modificar(99L, "C", true, true, true));
        assertThrows(TipoPermisoDuplicadoException.class, () -> servicio.modificar(a.id(), "b", true, true, true));
    }
}
