package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class EntidadTest {

    @Test
    void empresaInicializaLasColecciones() {
        Empresa empresa = new Empresa();

        assertNotNull(empresa.getChoferes());
        assertNotNull(empresa.getGuias());
        assertNotNull(empresa.getVehiculos());
        assertTrue(empresa.getChoferes().isEmpty());
    }

    @Test
    void choferMantieneSusPropiedades() {
        Empresa empresa = new Empresa();
        Chofer chofer = new Chofer("CI-1", "chofer@example.com", empresa);

        assertEquals("CI-1", chofer.getCi());
        assertEquals("chofer@example.com", chofer.getEmail());
        assertSame(empresa, chofer.getEmpresa());
    }

    @Test
    void responsableMantieneSusPropiedades() {
        Empresa empresa = new Empresa();
        Responsable responsable = new Responsable("CI-2", "responsable@example.com", empresa);

        assertEquals("CI-2", responsable.getCi());
        assertEquals("responsable@example.com", responsable.getEmail());
        assertSame(empresa, responsable.getEmpresa());
    }

    @Test
    void vehiculoMantieneSusPropiedades() {
        Empresa empresa = new Empresa();
        Vehiculo vehiculo = new Vehiculo("ABC-123", "Toyota", "Corolla", 1200L, 500L, empresa);

        assertEquals("ABC-123", vehiculo.getMatricula());
        assertEquals("Toyota", vehiculo.getMarca());
        assertEquals("Corolla", vehiculo.getModelo());
        assertEquals(1200L, vehiculo.getPeso());
        assertEquals(500L, vehiculo.getCapacidadCargaKg());
        assertSame(empresa, vehiculo.getEmpresa());
    }

    @Test
    void guiaMantieneSusPropiedadesYRelaciones() {
        Empresa empresa = new Empresa();
        Vehiculo vehiculo = new Vehiculo("ABC-123", "Toyota", "Corolla", 1200L, 500L, empresa);
        Chofer chofer = new Chofer("CI-1", "chofer@example.com", empresa);
        Guia guia = new Guia("GUIA-1", "Transporte", 100L, LocalDate.of(2026, 10, 7),
                "La Plata", "Buenos Aires", empresa);

        guia.setVehiculo(vehiculo);
        guia.setChofer(chofer);

        assertEquals("GUIA-1", guia.getId());
        assertEquals("Transporte", guia.getRubroCliente());
        assertEquals(100L, guia.getPesoCargaKg());
        assertEquals(LocalDate.of(2026, 10, 7), guia.getFecha());
        assertEquals("La Plata", guia.getOrigen());
        assertEquals("Buenos Aires", guia.getDestino());
        assertSame(empresa, guia.getEmpresa());
        assertSame(vehiculo, guia.getVehiculo());
        assertSame(chofer, guia.getChofer());
    }
}
