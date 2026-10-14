package tse.lab.fiscalizacionautomatica.ruleengine.vehicles;

import java.io.IOException;
import java.util.Optional;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFactsSource;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.ViajesEnCursoRemote;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.VehiculosRemote;

/** Vehicle and active trips (guides ASIGNADA or EN_CURSO), through the Frontofficeciudadanos remote EJBs. */
public class VehicleFactsClient implements VehicleFactsSource, AutoCloseable {

    private final Context context;
    private final VehiculosRemote vehicles;
    private final ViajesEnCursoRemote trips;

    /** @param url WildFly HTTP port, e.g. remote+http://wildfly:8080 */
    public VehicleFactsClient(String url, String user, String password) throws IOException {
        try {
            context = WildFlyContexts.create(url, user, password);
            // Only builds the proxies: the connection is opened on the first call
            vehicles = (VehiculosRemote) context.lookup(VehiculosRemote.LOOKUP);
            trips = (ViajesEnCursoRemote) context.lookup(ViajesEnCursoRemote.LOOKUP);
        } catch (NamingException e) {
            throw new IOException("Could not look up the Frontofficeciudadanos remote EJBs", e);
        }
    }

    @Override
    public Optional<VehicleFacts> find(String licensePlate) throws IOException {
        try {
            DatosVehiculo vehicle = vehicles.buscarVehiculo(licensePlate);
            if (vehicle == null) {
                return Optional.empty();
            }
            return Optional.of(new VehicleFacts(vehicle, trips.buscarViajesEnCurso(licensePlate)));
        } catch (RuntimeException e) {
            // EJBException (WildFly down, wrong credentials, EAR not deployed) or a failure reading the answer
            throw new IOException("Remote call to Frontofficeciudadanos failed", e);
        }
    }

    @Override
    public void close() {
        WildFlyContexts.closeQuietly(context);
    }
}
