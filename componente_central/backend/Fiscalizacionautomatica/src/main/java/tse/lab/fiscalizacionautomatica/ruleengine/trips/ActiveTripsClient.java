package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import jakarta.ejb.EJBException;
import java.io.IOException;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.ViajesEnCursoRemote;

/** Active trips (guides ASIGNADA or EN_CURSO) of a vehicle, through the Frontofficeciudadanos remote EJB. */
public class ActiveTripsClient implements AutoCloseable {

    private final Context context;
    private final ViajesEnCursoRemote trips;

    /** @param url WildFly HTTP port, e.g. remote+http://wildfly:8080 */
    public ActiveTripsClient(String url, String user, String password) throws IOException {
        try {
            context = WildFlyContexts.create(url, user, password);
            // Only builds the proxy: the connection is opened on the first call
            trips = (ViajesEnCursoRemote) context.lookup(ViajesEnCursoRemote.LOOKUP);
        } catch (NamingException e) {
            throw new IOException("Could not look up " + ViajesEnCursoRemote.LOOKUP, e);
        }
    }

    public List<DatosViaje> findActiveTrips(String licensePlate) throws IOException {
        try {
            return trips.buscarViajesEnCurso(licensePlate);
        } catch (EJBException e) {
            // WildFly down, wrong credentials or the EAR not deployed
            throw new IOException("Remote call to ViajesEnCursoService failed", e);
        }
    }

    @Override
    public void close() {
        WildFlyContexts.closeQuietly(context);
    }
}
