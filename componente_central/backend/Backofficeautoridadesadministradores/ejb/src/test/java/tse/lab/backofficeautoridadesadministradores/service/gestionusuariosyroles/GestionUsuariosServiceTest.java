package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioDAO;
import tse.lab.model.Ciudadano;
import tse.lab.model.Funcionario;

class GestionUsuariosServiceTest {

    private GestionUsuariosService servicio;
    private UsuarioDAOFalso usuarios;

    // DAO simulado para probar el servicio sin base de datos.
    static class UsuarioDAOFalso extends UsuarioDAO {

        final List<Ciudadano> ciudadanos = new ArrayList<>();
        final List<Funcionario> funcionarios = new ArrayList<>();
        final List<String> responsables = new ArrayList<>();
        final List<String> choferes = new ArrayList<>();
        final List<String> administradores = new ArrayList<>();
        final List<String> autoridades = new ArrayList<>();

        private List<String> conRol(RolUsuario rol) {
            return switch (rol) {
                case FUNCIONARIO -> funcionarios.stream().map(Funcionario::getCi).toList();
                case RESPONSABLE -> responsables;
                case CHOFER -> choferes;
                case ADMINISTRADOR -> administradores;
                case AUTORIDAD -> autoridades;
            };
        }

        private List<String> cedulas(RolUsuario rol, String prefijoCi) {
            TreeSet<String> cedulas = new TreeSet<>();
            for (RolUsuario r : RolUsuario.values()) {
                if (rol == null || rol == r) {
                    cedulas.addAll(conRol(r));
                }
            }
            return cedulas.stream()
                    .filter(ci -> prefijoCi == null || ci.startsWith(prefijoCi))
                    .toList();
        }

        private static List<String> filtrar(List<String> rol, List<String> cedulas) {
            return rol.stream().filter(cedulas::contains).toList();
        }

        @Override
        public long contarUsuarios(RolUsuario rol, String prefijoCi) {
            return cedulas(rol, prefijoCi).size();
        }

        @Override
        public List<String> listarCedulas(RolUsuario rol, String prefijoCi, int desde, int cantidad) {
            List<String> todas = cedulas(rol, prefijoCi);
            return todas.subList(desde, Math.min(desde + cantidad, todas.size()));
        }

        @Override
        public List<Ciudadano> buscarCiudadanos(List<String> cedulas) {
            return ciudadanos.stream().filter(c -> cedulas.contains(c.getCi())).toList();
        }

        @Override
        public List<Funcionario> buscarFuncionarios(List<String> cedulas) {
            return funcionarios.stream().filter(f -> cedulas.contains(f.getCi())).toList();
        }

        @Override
        public List<String> filtrarResponsables(List<String> cedulas) {
            return filtrar(responsables, cedulas);
        }

        @Override
        public List<String> filtrarChoferes(List<String> cedulas) {
            return filtrar(choferes, cedulas);
        }

        @Override
        public List<String> filtrarAdministradores(List<String> cedulas) {
            return filtrar(administradores, cedulas);
        }

        @Override
        public List<String> filtrarAutoridades(List<String> cedulas) {
            return filtrar(autoridades, cedulas);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        usuarios = new UsuarioDAOFalso();
        servicio = new GestionUsuariosService();
        inyectar(usuarios);
    }

    private void inyectar(UsuarioDAO dao) throws Exception {
        Field campo = GestionUsuariosService.class.getDeclaredField("usuarios");
        campo.setAccessible(true);
        campo.set(servicio, dao);
    }

    private static Ciudadano ciudadano(String ci, String email, String nombres, String apellidos) {
        Ciudadano ciudadano = new Ciudadano(ci, email, null);
        ciudadano.setNombres(nombres);
        ciudadano.setApellidos(apellidos);
        return ciudadano;
    }

    private List<DatosUsuario> primeraPagina() {
        return servicio.listar(null, null, null, null).usuarios();
    }

    @Test
    void sinUsuariosDevuelveUnaPaginaVacia() {
        assertEquals(new PaginaUsuarios(List.of(), 1, 20, 0, 0), servicio.listar(null, null, null, null));
    }

    @Test
    void incluyeTodosLosRolesOrdenadosPorCedula() {
        usuarios.autoridades.add("55555555");
        usuarios.administradores.add("44444444");
        usuarios.funcionarios.add(new Funcionario("33333333", "func@mtop.gub.uy"));
        usuarios.choferes.add("22222222");
        usuarios.responsables.add("11111111");
        usuarios.ciudadanos.add(ciudadano("11111111", "ana@example.com", "Ana", "Perez"));

        assertEquals(
                List.of(
                        new DatosUsuario("11111111", "Ana", "Perez", "ana@example.com",
                                List.of(RolUsuario.RESPONSABLE)),
                        new DatosUsuario("22222222", null, null, null,
                                List.of(RolUsuario.CHOFER)),
                        new DatosUsuario("33333333", null, null, "func@mtop.gub.uy",
                                List.of(RolUsuario.FUNCIONARIO)),
                        new DatosUsuario("44444444", null, null, null,
                                List.of(RolUsuario.ADMINISTRADOR)),
                        new DatosUsuario("55555555", null, null, null,
                                List.of(RolUsuario.AUTORIDAD))),
                primeraPagina());
    }

    @Test
    void unaCedulaConVariosRolesEsUnaSolaFila() {
        usuarios.ciudadanos.add(ciudadano("11111111", null, "Ana", "Perez"));
        usuarios.funcionarios.add(new Funcionario("11111111", "func@mtop.gub.uy"));
        usuarios.responsables.add("11111111");
        usuarios.choferes.add("11111111");

        List<DatosUsuario> resultado = primeraPagina();

        assertEquals(1, resultado.size());
        assertEquals(
                List.of(RolUsuario.FUNCIONARIO, RolUsuario.RESPONSABLE, RolUsuario.CHOFER),
                resultado.get(0).roles());
        assertEquals("Ana", resultado.get(0).nombres());
    }

    @Test
    void elEmailDelCiudadanoTienePrioridadSobreElDelFuncionario() {
        usuarios.ciudadanos.add(ciudadano("11111111", "ana@example.com", null, null));
        usuarios.funcionarios.add(new Funcionario("11111111", "func@mtop.gub.uy"));
        usuarios.ciudadanos.add(ciudadano("22222222", null, null, null));
        usuarios.funcionarios.add(new Funcionario("22222222", "beto@mtop.gub.uy"));

        List<DatosUsuario> resultado = primeraPagina();

        assertEquals("ana@example.com", resultado.get(0).email());
        assertEquals("beto@mtop.gub.uy", resultado.get(1).email());
    }

    @Test
    void devuelveLaPaginaPedidaConLosTotales() {
        usuarios.choferes.addAll(List.of("1", "2", "3", "4", "5"));

        PaginaUsuarios pagina = servicio.listar("2", "2", null, null);

        assertEquals(List.of("3", "4"), pagina.usuarios().stream().map(DatosUsuario::ci).toList());
        assertEquals(2, pagina.pagina());
        assertEquals(2, pagina.tamanio());
        assertEquals(5, pagina.total());
        assertEquals(3, pagina.totalPaginas());
    }

    @Test
    void unaPaginaPosteriorALaUltimaVieneVacia() {
        usuarios.choferes.add("1");

        assertTrue(servicio.listar("2", "20", null, null).usuarios().isEmpty());
        // (2147483647 - 1) * 100 no entra en un int
        assertTrue(servicio.listar("2147483647", "100", null, null).usuarios().isEmpty());
    }

    @Test
    void siSeBorranUsuariosTrasElConteoLaPaginaVieneVacia() throws Exception {
        inyectar(new UsuarioDAOFalso() {
            @Override
            public long contarUsuarios(RolUsuario rol, String prefijoCi) {
                return 1;
            }
        });

        assertTrue(servicio.listar(null, null, null, null).usuarios().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "0,20", "-1,20", "x,20", "1,0", "1,101", "1,x", "1.5,20" })
    void rechazaLosParametrosInvalidos(String pagina, String tamanio) {
        assertThrows(PaginaInvalidaException.class, () -> servicio.listar(pagina, tamanio, null, null));
    }

    @Test
    void aceptaElTamanioMaximo() {
        assertEquals(100, servicio.listar("1", "100", null, null).tamanio());
    }

    private List<String> cedulasCon(String rol, String ci) {
        return servicio.listar(null, null, rol, ci).usuarios().stream()
                .map(DatosUsuario::ci).toList();
    }

    @Test
    void filtraPorRolYMuestraTodosLosRolesDeLaPersona() {
        usuarios.choferes.addAll(List.of("11111111", "22222222"));
        usuarios.funcionarios.add(new Funcionario("22222222", "func@mtop.gub.uy"));
        usuarios.administradores.add("33333333");

        PaginaUsuarios funcionarios = servicio.listar(null, null, "FUNCIONARIO", null);

        assertEquals(1, funcionarios.total());
        assertEquals(List.of(RolUsuario.FUNCIONARIO, RolUsuario.CHOFER),
                funcionarios.usuarios().get(0).roles());
        assertEquals(List.of("11111111", "22222222"), cedulasCon("chofer", null));
        assertTrue(cedulasCon("AUTORIDAD", null).isEmpty());
    }

    @Test
    void buscaPorElComienzoDeLaCedula() {
        usuarios.choferes.addAll(List.of("54074380", "54099999", "56087406"));

        assertEquals(List.of("54074380", "54099999"), cedulasCon(null, "540"));
        assertEquals(List.of("54074380"), cedulasCon(null, "5.407.438-0"));
        assertEquals(List.of("54074380"), cedulasCon("CHOFER", " 5407 "));
        assertTrue(cedulasCon(null, "9").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "CIUDADANO,", "x,", ",abc", ",123456789", ",54a" })
    void rechazaFiltrosInvalidos(String rol, String ci) {
        assertThrows(PaginaInvalidaException.class, () -> servicio.listar(null, null, rol, ci));
    }
}
