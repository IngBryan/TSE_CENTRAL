package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import java.io.IOException;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule.PruebaDesvio;
import tse.lab.frontofficefuncionarios.service.gestioncasos.PruebaDesvioRemota;
import tse.lab.frontofficefuncionarios.service.gestioncasos.PosicionSinGuiaRemota;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;
import tse.lab.frontofficefuncionarios.service.gestioncasos.CasosAutomaticosRemote;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.EventoFiscalizado;
import tse.lab.frontofficefuncionarios.service.gestioncasos.OrigenEvento;
import tse.lab.frontofficefuncionarios.service.gestioncasos.TipoCasoAutomatico;

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

    /** With the remote proxy already looked up (tests). */
    RemoteCaseStore(Context context, CasosAutomaticosRemote cases) {
        this.context = context;
        this.cases = cases;
    }

    @Override
    public DatosCaso registrar(TipoCasoAutomatico tipo, String idGuia, VehicleEvent evento) throws IOException {
        return call(() -> cases.registrarEvidencia(tipo, idGuia, toEventoFiscalizado(evento)));
    }

    @Override
    public DatosCaso registrarDesvio(String idGuia, TrackingEvent evento, PruebaDesvio prueba)
            throws IOException {
        if (prueba == null || !prueba.confirmante().equals(evento)) {
            throw new IOException("La prueba no corresponde al GPS que se registra");
        }
        var pareja = prueba.pareja();
        PruebaDesvioRemota remota = new PruebaDesvioRemota(pareja.timestamp(),
                evento.latitude(), evento.longitude(), pareja.latitude(), pareja.longitude(),
                prueba.distanciaConfirmanteMetros(), prueba.distanciaParejaMetros(),
                prueba.umbralMetros(), prueba.ventanaMaximaSegundos(), prueba.hashesRutas());
        return call(() -> cases.registrarDesvio(idGuia, toEventoFiscalizado(evento), remota));
    }

    @Override
    public DatosCaso registrarCirculacionSinGuia(TrackingEvent gps) throws IOException {
        return call(() -> cases.registrarCirculacionSinGuia(new PosicionSinGuiaRemota(
                gps.licensePlate(), gps.timestamp(), gps.latitude(), gps.longitude())));
    }

    @Override
    public void cerrarCirculacionPorGuia(String matricula, java.time.Instant fecha) throws IOException {
        call(() -> { cases.cerrarCirculacionPorGuia(matricula, fecha); return null; });
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
