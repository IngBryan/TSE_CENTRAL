package tse.lab.frontofficeciudadanos.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EmpresaTest {

    @Test
    void constructorVacioInicializaColeccionesVacias() {
        Empresa empresa = new Empresa();

        assertAll(
                () -> assertNotNull(empresa.getChoferes()),
                () -> assertNotNull(empresa.getGuias()),
                () -> assertNotNull(empresa.getVehiculos()),
                () -> assertTrue(empresa.getChoferes().isEmpty()),
                () -> assertTrue(empresa.getGuias().isEmpty()),
                () -> assertTrue(empresa.getVehiculos().isEmpty()));
    }

    @Test
    void constructorConDatosInicializaPropiedadesYColeccionesVacias() {
        Empresa empresa = new Empresa(42L, "Nombre de la empresa", "Razón social", "Dirección");

        assertAll(
                () -> assertEquals(42L, empresa.getNroEmpresa()),
                () -> assertEquals("Nombre de la empresa", empresa.getNombrePublico()),
                () -> assertEquals("Razón social", empresa.getRazonSocial()),
                () -> assertEquals("Dirección", empresa.getDireccion()),
                () -> assertTrue(empresa.getChoferes().isEmpty()),
                () -> assertTrue(empresa.getGuias().isEmpty()),
                () -> assertTrue(empresa.getVehiculos().isEmpty()));
    }

    @Test
    void settersActualizanLosDatosYLasRelacionesDeLaEmpresa() {
        Empresa empresa = new Empresa();
        Chofer chofer = new Chofer("CI-3", "chofer@example.com", empresa);
        Guia guia = new Guia("GUIA-1", "Transporte", 100L, null, "Orígen", "Destino", empresa);
        Vehiculo vehiculo = new Vehiculo("ABC-123", "Toyota", "Corolla", 1200L, 500L, empresa);

        empresa.setNroEmpresa(99L);
        empresa.setNombrePublico("Nueva empresa");
        empresa.setRazonSocial("Nueva razón social");
        empresa.setDireccion("Nueva dirección");
        empresa.setChoferes(new ArrayList<>(List.of(chofer)));
        empresa.setGuias(new ArrayList<>(List.of(guia)));
        empresa.setVehiculos(new ArrayList<>(List.of(vehiculo)));

        assertAll(
                () -> assertEquals(99L, empresa.getNroEmpresa()),
                () -> assertEquals("Nueva empresa", empresa.getNombrePublico()),
                () -> assertEquals("Nueva razón social", empresa.getRazonSocial()),
                () -> assertEquals("Nueva dirección", empresa.getDireccion()),
                () -> assertEquals(List.of(chofer), empresa.getChoferes()),
                () -> assertEquals(List.of(guia), empresa.getGuias()),
                () -> assertEquals(List.of(vehiculo), empresa.getVehiculos()));
    }

    @Test
    void lasColeccionesPuedenSerModificadasPorElConsumidor() {
        Empresa empresa = new Empresa();
        Chofer chofer = new Chofer("CI-4", "chofer@example.com", empresa);
        Guia guia = new Guia("GUIA-2", "Carga", 10L, null, "Orígen", "Destino", empresa);
        Vehiculo vehiculo = new Vehiculo("XYZ-789", "Ford", "Fiesta", 1000L, 200L, empresa);

        empresa.getChoferes().add(chofer);
        empresa.getGuias().add(guia);
        empresa.getVehiculos().add(vehiculo);

        assertAll(
                () -> assertEquals(List.of(chofer), empresa.getChoferes()),
                () -> assertEquals(List.of(guia), empresa.getGuias()),
                () -> assertEquals(List.of(vehiculo), empresa.getVehiculos()),
                () -> assertFalse(empresa.getChoferes().isEmpty()),
                () -> assertFalse(empresa.getGuias().isEmpty()),
                () -> assertFalse(empresa.getVehiculos().isEmpty()));
    }
}
