package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// DTO del EJB remoto de vehiculos: viaja por valor, asi que equals y hashCode tienen que comparar todo.
class DatosVehiculoTest {

    @Test
    void getters() {
        DatosVehiculo v = new DatosVehiculo("SBC1234", 1L, 8500, 25000);
        assertEquals("SBC1234", v.getMatricula());
        assertEquals(1L, v.getNroEmpresa());
        assertEquals(8500, v.getPesoKg());
        assertEquals(25000, v.getCapacidadCargaKg());
    }

    @Test
    void igualesSiCoincidenTodosLosCampos() {
        assertEquals(new DatosVehiculo("SBC1234", 1L, 8500, 25000), new DatosVehiculo("SBC1234", 1L, 8500, 25000));
        assertEquals(new DatosVehiculo("SBC1234", 1L, 8500, 25000).hashCode(),
                new DatosVehiculo("SBC1234", 1L, 8500, 25000).hashCode());
        assertTrue(new DatosVehiculo("SBC1234", 1L, 8500, 25000).toString().contains("SBC1234"));
    }

    @Test
    void distintosSiCambiaCualquierCampo() {
        DatosVehiculo v = new DatosVehiculo("SBC1234", 1L, 8500, 25000);
        assertNotEquals(v, null);
        Object otraClase = "SBC1234";
        assertNotEquals(v, otraClase);
        assertNotEquals(v, new DatosVehiculo("STU5678", 1L, 8500, 25000));
        assertNotEquals(v, new DatosVehiculo("SBC1234", 2L, 8500, 25000));
        assertNotEquals(v, new DatosVehiculo("SBC1234", 1L, 9000, 25000));
        assertNotEquals(v, new DatosVehiculo("SBC1234", 1L, 8500, 26000));
    }
}
