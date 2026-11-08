package tse.lab.fiscalizacionautomatica.ruleengine.vehicles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ejb.EJBException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.naming.Context;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.ViajesEnCursoRemote;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.VehiculosRemote;
import tse.lab.model.EvidenciaHabilitacion;

class VehicleFactsClientTest {

    private final Context context = mock(Context.class);
    private final VehiculosRemote vehicles = mock(VehiculosRemote.class);
    private final ViajesEnCursoRemote trips = mock(ViajesEnCursoRemote.class);
    private final VehicleFactsClient client = new VehicleFactsClient(context, vehicles, trips);

    @Test
    void aRegisteredVehicleComesWithItsActiveTrips() throws IOException {
        DatosVehiculo vehicle = new DatosVehiculo("SBC1234", 1L, 9_000, 25_000);
        DatosViaje trip = new DatosViaje("GV-1", "SBC1234", 1L, "EN_CURSO", LocalDate.of(2026, 10, 19),
                Instant.parse("2026-10-19T10:00:00Z"), "Montevideo", "Salto", 18_000);
        when(vehicles.buscarVehiculo("SBC1234")).thenReturn(vehicle);
        when(trips.buscarViajesEnCurso("SBC1234")).thenReturn(List.of(trip));

        assertEquals(Optional.of(new VehicleFacts(vehicle, List.of(trip))), client.find("SBC1234"));
    }

    @Test
    void consultaDocumentosConLaFechaDeMedicionYPropagaFallas() throws IOException {
        Instant fecha = Instant.parse("2026-11-06T12:00:00Z");
        var prueba = new EvidenciaHabilitacion("SBC1234", fecha, null, null, null, null, null);
        var vehiculo = new DatosVehiculo("SBC1234", 1L, 9000, 25000, prueba);
        when(vehicles.buscarVehiculo("SBC1234", fecha)).thenReturn(vehiculo);
        when(trips.buscarViajesEnCurso("SBC1234")).thenReturn(List.of());
        assertEquals(prueba, client.find("SBC1234", fecha).orElseThrow().vehicle().getHabilitacion());
        verify(vehicles).buscarVehiculo("SBC1234", fecha);
        when(vehicles.buscarVehiculo("SBC1234", fecha)).thenThrow(new EJBException("fallo"));
        assertThrows(IOException.class, () -> client.find("SBC1234", fecha));
    }

    @Test
    void anUnknownVehicleHasNoFacts() throws IOException {
        assertEquals(Optional.empty(), client.find("XXX0000"));
        verifyNoInteractions(trips);
    }

    @Test
    void aFailedRemoteCallIsAnIOException() {
        when(vehicles.buscarVehiculo("SBC1234")).thenThrow(new EJBException("WildFly down"));

        assertThrows(IOException.class, () -> client.find("SBC1234"));
    }

    @Test
    void closingClosesTheContext() throws Exception {
        client.close();

        verify(context).close();
    }
}
