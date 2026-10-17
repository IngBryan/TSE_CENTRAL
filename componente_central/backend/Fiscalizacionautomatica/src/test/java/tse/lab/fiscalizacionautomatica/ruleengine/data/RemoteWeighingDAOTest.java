package tse.lab.fiscalizacionautomatica.ruleengine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosPesaje;

class RemoteWeighingDAOTest {

    @Test
    void weighingGoesToTheEjbAndComesBackTheSame() {
        Weighing weighing = new Weighing(3, 4, "SBC1234", Instant.parse("2026-10-13T10:00:00Z"), 18_450);

        DatosPesaje datos = RemoteWeighingDAO.toDatos(weighing);

        assertEquals(new DatosPesaje(3, 4, "SBC1234", Instant.parse("2026-10-13T10:00:00Z"), 18_450), datos);
        assertEquals(weighing, RemoteWeighingDAO.toWeighing(datos));
    }
}
