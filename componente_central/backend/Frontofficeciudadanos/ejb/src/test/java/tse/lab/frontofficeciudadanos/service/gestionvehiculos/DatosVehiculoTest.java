package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// DTO del EJB remoto de vehiculos: viaja por valor, asi que equals y hashCode tienen que comparar todo.
class DatosVehiculoTest {

    @Test
    void getters() {
        DatosVehiculo v = new DatosVehiculo("SBC1234", 1L);
        assertEquals("SBC1234", v.getMatricula());
        assertEquals(1L, v.getNroEmpresa());
    }

    @Test
    void igualesSiCoincidenTodosLosCampos() {
        assertEquals(new DatosVehiculo("SBC1234", 1L), new DatosVehiculo("SBC1234", 1L));
        assertEquals(new DatosVehiculo("SBC1234", 1L).hashCode(), new DatosVehiculo("SBC1234", 1L).hashCode());
        assertTrue(new DatosVehiculo("SBC1234", 1L).toString().contains("SBC1234"));
    }

    @Test
    void distintosSiCambiaCualquierCampo() {
        DatosVehiculo v = new DatosVehiculo("SBC1234", 1L);
        assertNotEquals(v, null);
        assertNotEquals(v, "SBC1234");
        assertNotEquals(v, new DatosVehiculo("STU5678", 1L));
        assertNotEquals(v, new DatosVehiculo("SBC1234", 2L));
    }
}
