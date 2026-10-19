package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import java.io.IOException;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;
import tse.lab.frontofficefuncionarios.service.gestioncasos.CasosAutomaticosRemote;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.EventoFiscalizado;
import tse.lab.frontofficefuncionarios.service.gestioncasos.OrigenEvento;

/** Fiscalization cases in PostgreSQL, through the Frontofficefuncionarios remote EJB. */
public class RemoteCaseStore implements CaseStore, AutoCloseable {

    /** A remote call; any failure is turned into an IOException. */
    private interface Call<T> {
        T run();
    }

    private final Context context;
    private final CasosAutomaticosRemote cases;

    /** @param url WildFly HTTP port, e.g. remote+http://wildfly:8080 */
    public RemoteCaseStore(String url, String user, String password) throws IOException {
        try {
            context = WildFlyContexts.create(url, user, password);
            cases = (CasosAutomaticosRemote) context.lookup(CasosAutomaticosRemote.LOOKUP);
        } catch (NamingException e) {
            throw new IOException("Could not look up " + CasosAutomaticosRemote.LOOKUP, e);
        }
    }

    @Override
    public List<DatosCaso> openCases(String licensePlate) throws IOException {
        return call(() -> cases.casosAbiertos(licensePlate));
    }

    @Override
    public DatosCaso caseOf(VehicleEvent event) throws IOException {
        return call(() -> cases.casoDelEvento(toEventoFiscalizado(event)));
    }

    @Override
    public DatosCaso create(String caseType, String guideId, VehicleEvent event) throws IOException {
        return call(() -> cases.crearCaso(caseType, guideId, toEventoFiscalizado(event)));
    }

    @Override
    public void addEvidence(long caseId, VehicleEvent event) throws IOException {
        call(() -> {
            cases.agregarEvidencia(caseId, toEventoFiscalizado(event));
            return null;
        });
    }

    @Override
    public DatosCaso notify(long caseId) throws IOException {
        return call(() -> cases.notificarCaso(caseId));
    }

    private static <T> T call(Call<T> call) throws IOException {
        try {
            return call.run();
        } catch (RuntimeException e) {
            // EJBException (WildFly down, wrong credentials, EAR not deployed, a constraint of the database)
            // or a failure reading the answer
            throw new IOException("Remote call to CasosAutomaticosService failed", e);
        }
    }

    static EventoFiscalizado toEventoFiscalizado(VehicleEvent event) {
        OrigenEvento origin = switch (event) {
            case TrackingEvent tracking -> OrigenEvento.TRACKING;
            case Weighing weighing -> OrigenEvento.PESAJE;
        };
        return new EventoFiscalizado(event.licensePlate(), event.timestamp(), origin);
    }

    @Override
    public void close() {
        WildFlyContexts.closeQuietly(context);
    }
}
