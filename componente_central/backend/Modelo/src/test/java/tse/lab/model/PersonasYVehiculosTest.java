package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

// Ciudadanos, choferes, responsables, usuarios del backoffice, empresas y la documentacion de los vehiculos.
class PersonasYVehiculosTest {

    @Test
    void elCiudadanoNuevoQuedaPendienteDeValidacion() {
        Ciudadano ciudadano = new Ciudadano("11111111", "ciudadano@example.com", "sub-1");

        assertEquals("11111111", ciudadano.getCi());
        assertEquals("ciudadano@example.com", ciudadano.getEmail());
        assertEquals("sub-1", ciudadano.getSubGubUy());
        assertEquals(EstadoValidacion.PENDIENTE_VALIDACION, ciudadano.getEstado());
        assertNotNull(ciudadano.getCreado());
        assertNull(ciudadano.getUltimoIngreso());
    }

    @Test
    void losDatosDelCiudadanoSePuedenCompletar() {
        Ciudadano ciudadano = new Ciudadano();
        Instant ahora = Instant.now();

        ciudadano.setCi("22222222");
        ciudadano.setEmail("otro@example.com");
        ciudadano.setSubGubUy("sub-2");
        ciudadano.setNombres("Ana Maria");
        ciudadano.setApellidos("Perez Gomez");
        ciudadano.setPrimerNombre("Ana");
        ciudadano.setPrimerApellido("Perez");
        ciudadano.setFechaNacimiento(LocalDate.of(1990, 5, 1));
        ciudadano.setEstado(EstadoValidacion.VALIDADO);
        ciudadano.setUltimoIngreso(ahora);
        ciudadano.setUltimaValidacion(ahora);

        assertEquals("22222222", ciudadano.getCi());
        assertEquals("otro@example.com", ciudadano.getEmail());
        assertEquals("sub-2", ciudadano.getSubGubUy());
        assertEquals("Ana Maria", ciudadano.getNombres());
        assertEquals("Perez Gomez", ciudadano.getApellidos());
        assertEquals("Ana", ciudadano.getPrimerNombre());
        assertEquals("Perez", ciudadano.getPrimerApellido());
        assertEquals(LocalDate.of(1990, 5, 1), ciudadano.getFechaNacimiento());
        assertEquals(EstadoValidacion.VALIDADO, ciudadano.getEstado());
        assertEquals(ahora, ciudadano.getUltimoIngreso());
        assertEquals(ahora, ciudadano.getUltimaValidacion());
        assertNull(ciudadano.getCreado());
    }

    @Test
    void elChoferNaceHabilitadoSalvoQueSeIndiqueOtroEstado() {
        Empresa empresa = new Empresa();
        Ciudadano ciudadano = new Ciudadano("11111111", "chofer@example.com", null);

        Chofer habilitado = new Chofer(ciudadano, empresa);
        Chofer pendiente = new Chofer(ciudadano, empresa, EstadoChofer.PENDIENTE_VALIDACION);

        assertEquals(EstadoChofer.HABILITADO, habilitado.getEstado());
        assertNotNull(habilitado.getFechaAlta());
        assertNull(habilitado.getId());
        assertEquals(EstadoChofer.PENDIENTE_VALIDACION, pendiente.getEstado());

        Empresa otra = new Empresa();
        habilitado.setEmpresa(otra);
        habilitado.setEstado(EstadoChofer.PENDIENTE_VALIDACION);
        assertSame(otra, habilitado.getEmpresa());
        assertEquals(EstadoChofer.PENDIENTE_VALIDACION, habilitado.getEstado());

        // sin ciudadano (constructor de JPA) no tiene ci
        assertNull(new Chofer().getCi());
    }

    @Test
    void unChoferDadoDeBajaSeReactivaConUnaNuevaFechaDeAlta() throws InterruptedException {
        Chofer chofer = new Chofer(new Ciudadano("11111111", null, null), new Empresa());
        Instant altaOriginal = chofer.getFechaAlta();
        assertFalse(chofer.isDadoDeBaja());

        chofer.setEstado(EstadoChofer.DADO_DE_BAJA);
        assertTrue(chofer.isDadoDeBaja());

        Thread.sleep(2);
        chofer.reactivar(EstadoChofer.HABILITADO);
        assertFalse(chofer.isDadoDeBaja());
        assertEquals(EstadoChofer.HABILITADO, chofer.getEstado());
        assertTrue(chofer.getFechaAlta().isAfter(altaOriginal));
    }

    @Test
    void elResponsableCambiaDeEmpresa() {
        Responsable responsable = new Responsable(new Ciudadano("22222222", "r@example.com", null), new Empresa());
        Empresa otra = new Empresa();

        responsable.setEmpresa(otra);

        assertSame(otra, responsable.getEmpresa());
        assertNull(new Responsable().getCi());
    }

    @Test
    void losUsuariosDelBackofficeSonAutoridadesOAdministradores() {
        UsuarioBackoffice autoridad = new Autoridad("44444444", "hash-1");
        UsuarioBackoffice administrador = new Administrador("55555555", "hash-2");

        assertEquals("44444444", autoridad.getCi());
        assertEquals("hash-1", autoridad.getContrasena());
        assertEquals("55555555", administrador.getCi());

        administrador.setCi("66666666");
        administrador.setContrasena("hash-3");
        assertEquals("66666666", administrador.getCi());
        assertEquals("hash-3", administrador.getContrasena());
        assertNull(new Autoridad().getCi());
        assertNull(new Administrador().getCi());
    }

    @Test
    void laEmpresaMantieneSusDatosYColecciones() {
        Responsable responsable = new Responsable();
        Empresa empresa = new Empresa(10L, "Transportes Sur", "Transportes Sur SA", "Ruta 5 km 30", responsable);

        assertEquals(10L, empresa.getNroEmpresa());
        assertSame(responsable, empresa.getResponsable());
        assertTrue(empresa.getVehiculos().isEmpty());

        Chofer chofer = new Chofer();
        Guia guia = new Guia();
        Vehiculo vehiculo = new Vehiculo();
        empresa.setNroEmpresa(11L);
        empresa.setNombrePublico("Sur");
        empresa.setRazonSocial("Sur SRL");
        empresa.setDireccion("Ruta 1 km 10");
        empresa.setHabilitada(true);
        empresa.setResponsable(null);
        empresa.setChoferes(List.of(chofer));
        empresa.setGuias(List.of(guia));
        empresa.setVehiculos(List.of(vehiculo));

        assertEquals(11L, empresa.getNroEmpresa());
        assertEquals("Sur", empresa.getNombrePublico());
        assertEquals("Sur SRL", empresa.getRazonSocial());
        assertEquals("Ruta 1 km 10", empresa.getDireccion());
        assertTrue(empresa.isHabilitada());
        assertNull(empresa.getResponsable());
        assertEquals(List.of(chofer), empresa.getChoferes());
        assertEquals(List.of(guia), empresa.getGuias());
        assertEquals(List.of(vehiculo), empresa.getVehiculos());
    }

    @Test
    void losDatosDelVehiculoSePuedenEditar() {
        Vehiculo vehiculo = new Vehiculo();
        Empresa empresa = new Empresa();

        vehiculo.setMatricula("STU5678");
        vehiculo.setMarca("Scania");
        vehiculo.setModelo("R450");
        vehiculo.setPeso(10_000L);
        vehiculo.setCapacidadCargaKg(28_000L);
        vehiculo.setEmpresa(empresa);

        assertEquals("STU5678", vehiculo.getMatricula());
        assertEquals("Scania", vehiculo.getMarca());
        assertEquals("R450", vehiculo.getModelo());
        assertEquals(10_000L, vehiculo.getPeso());
        assertEquals(28_000L, vehiculo.getCapacidadCargaKg());
        assertSame(empresa, vehiculo.getEmpresa());
    }

    @Test
    void elPermisoDeCirculacionYLaItvTienenVigencia() {
        Vehiculo vehiculo = new Vehiculo();
        Vehiculo otro = new Vehiculo();
        LocalDate emision = LocalDate.of(2026, 1, 1);
        LocalDate vencimiento = LocalDate.of(2027, 1, 1);

        PermisoCirculacion permiso = new PermisoCirculacion("PC-1", emision, vencimiento, vehiculo);
        assertEquals("PC-1", permiso.getNro());
        assertEquals(emision, permiso.getFechaEmision());
        assertEquals(vencimiento, permiso.getFechaVencimiento());
        assertSame(vehiculo, permiso.getVehiculo());
        permiso.setNro("PC-2");
        permiso.setFechaEmision(vencimiento);
        permiso.setFechaVencimiento(vencimiento.plusYears(1));
        permiso.setVehiculo(otro);
        assertEquals("PC-2", permiso.getNro());
        assertEquals(vencimiento, permiso.getFechaEmision());
        assertEquals(vencimiento.plusYears(1), permiso.getFechaVencimiento());
        assertSame(otro, permiso.getVehiculo());
        assertNull(new PermisoCirculacion().getNro());

        ITV itv = new ITV(emision, vencimiento, vehiculo);
        assertNull(itv.getId());
        assertEquals(emision, itv.getFechaEmision());
        assertEquals(vencimiento, itv.getFechaVencimiento());
        assertSame(vehiculo, itv.getVehiculo());
        itv.setFechaEmision(vencimiento);
        itv.setFechaVencimiento(vencimiento.plusYears(1));
        itv.setVehiculo(otro);
        assertEquals(vencimiento, itv.getFechaEmision());
        assertEquals(vencimiento.plusYears(1), itv.getFechaVencimiento());
        assertSame(otro, itv.getVehiculo());
        assertNull(new ITV().getVehiculo());
    }

    @Test
    void elPermisoEstaVigenteEntreLaEmisionYElVencimientoIncluidos() {
        PermisoCirculacion permiso = new PermisoCirculacion("PC-1", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), new Vehiculo());

        assertFalse(permiso.vigenteEn(LocalDate.of(2025, 12, 31)));
        assertTrue(permiso.vigenteEn(LocalDate.of(2026, 1, 1)));
        assertTrue(permiso.vigenteEn(LocalDate.of(2026, 12, 31)));
        assertFalse(permiso.vigenteEn(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void laItvEstaVigenteHastaElVencimientoIncluido() {
        ITV itv = new ITV(LocalDate.of(2025, 6, 30), LocalDate.of(2026, 6, 30), new Vehiculo());

        assertTrue(itv.vigenteEn(LocalDate.of(2026, 6, 30)));
        assertFalse(itv.vigenteEn(LocalDate.of(2026, 7, 1)));
    }

    @Test
    void elVehiculoEstaHabilitadoSiEstaActivoConPermisoEItvVigentes() {
        LocalDate hoy = LocalDate.of(2026, 6, 1);
        Vehiculo vehiculo = new Vehiculo("SBA1234", "Volvo", "FH", 15_000, 25_000, null);
        PermisoCirculacion permiso = new PermisoCirculacion("PC-1", hoy.minusYears(1), hoy, vehiculo);
        ITV vencida = new ITV(hoy.minusYears(1), hoy.minusDays(1), vehiculo);
        ITV vigente = new ITV(hoy.minusDays(1), hoy.plusMonths(6), vehiculo);

        assertTrue(vehiculo.habilitadoEn(hoy, permiso, List.of(vencida, vigente)));
        // Sin permiso, con el permiso vencido o sin una ITV vigente
        assertFalse(vehiculo.habilitadoEn(hoy, null, List.of(vigente)));
        assertFalse(vehiculo.habilitadoEn(hoy.plusDays(1), permiso, List.of(vigente)));
        assertFalse(vehiculo.habilitadoEn(hoy, permiso, List.of(vencida)));
        assertFalse(vehiculo.habilitadoEn(hoy, permiso, List.of()));
        // Dado de baja, aunque los papeles esten al dia
        vehiculo.setActivo(false);
        assertFalse(vehiculo.habilitadoEn(hoy, permiso, List.of(vigente)));
    }
}
