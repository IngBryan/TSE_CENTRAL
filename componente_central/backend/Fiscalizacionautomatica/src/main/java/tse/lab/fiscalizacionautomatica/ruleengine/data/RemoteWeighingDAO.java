package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.io.IOException;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingException;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosPesaje;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.PesajesRemote;

/** Weighings saved in PostgreSQL (eventos_pesajes), through the Frontofficeciudadanos remote EJB. */
public class RemoteWeighingDAO implements WeighingDAO, AutoCloseable {

    private final Context context;
    private final PesajesRemote weighings;

    /** @param url WildFly HTTP port, e.g. remote+http://wildfly:8080 */
    public RemoteWeighingDAO(String url, String user, String password) throws IOException {
        try {
            context = WildFlyContexts.create(url, user, password);
            weighings = (PesajesRemote) context.lookup(PesajesRemote.LOOKUP);
        } catch (NamingException e) {
            throw new IOException("Could not look up " + PesajesRemote.LOOKUP, e);
        }
    }

    /** With the remote proxy already looked up (tests). */
    RemoteWeighingDAO(Context context, PesajesRemote weighings) {
        this.context = context;
        this.weighings = weighings;
    }

    @Override
    public int save(List<Weighing> toSave) throws IOException {
        try {
            return weighings.guardarPesajes(toSave.stream().map(RemoteWeighingDAO::toDatos).toList());
        } catch (RuntimeException e) {
            throw failed(e);
        }
    }

    @Override
    public List<Weighing> findPending(int limit) throws IOException {
        try {
            return weighings.listarPendientes(limit).stream().map(RemoteWeighingDAO::toWeighing).toList();
        } catch (RuntimeException e) {
            throw failed(e);
        }
    }

    @Override
    public long fijarRevisionReglas(Weighing w, long revision) throws IOException {
        try { return weighings.fijarRevisionReglas(w.licensePlate(), w.timestamp(), revision); }
        catch (RuntimeException e) { throw failed(e); }
    }

    @Override
    public void markDetected(Weighing weighing) throws IOException {
        try {
            weighings.marcarDetectado(weighing.licensePlate(), weighing.timestamp());
        } catch (RuntimeException e) {
            throw failed(e);
        }
    }

    /** EJBException (WildFly down, wrong credentials, EAR not deployed) or a failure reading the answer. */
    private static IOException failed(RuntimeException e) {
        return new IOException("Remote call to PesajesService failed", e);
    }

    static DatosPesaje toDatos(Weighing w) {
        return new DatosPesaje(w.weighingId(), w.scaleId(), w.licensePlate(), w.timestamp(), w.weightKg());
    }

    static Weighing toWeighing(DatosPesaje p) {
        return new Weighing(p.getIdExterno(), p.getIdExternoBalanza(), p.getMatricula(), p.getFechaHora(),
                p.getPesoKg());
    }

    @Override
    public void close() {
        WildFlyContexts.closeQuietly(context);
    }
}
