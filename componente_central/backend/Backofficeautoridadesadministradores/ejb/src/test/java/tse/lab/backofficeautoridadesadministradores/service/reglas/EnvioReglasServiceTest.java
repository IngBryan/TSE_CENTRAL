package tse.lab.backofficeautoridadesadministradores.service.reglas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import jakarta.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tse.lab.backofficeautoridadesadministradores.data.ReglasDAO;
import tse.lab.model.RevisionReglas;
import tse.lab.reglas.ConfiguracionReglas;

class EnvioReglasServiceTest {
    private final ReglasDAO dao=mock(ReglasDAO.class);
    private final Connection conexion=mock(Connection.class);
    private final Channel canal=mock(Channel.class);
    private final EnvioReglasService servicio=new EnvioReglasService();
    private final RevisionReglas revision=new RevisionReglas(ConfiguracionReglas.inicial(),"corr-1");

    @BeforeEach void preparar() throws Exception {
        Field campo=EnvioReglasService.class.getDeclaredField("reglas");
        campo.setAccessible(true); campo.set(servicio,dao);
        when(conexion.createChannel()).thenReturn(canal);
    }

    @Test void sinPendientesNoAbreUnaConexion() throws Exception {
        try (var fabricas=mockConstruction(ConnectionFactory.class)) {
            assertFalse(servicio.publicarSiguiente());
            assertTrue(fabricas.constructed().isEmpty());
        }
    }

    @Test void soloMarcaPublicadaDespuesDeLaConfirmacionDelBroker() throws Exception {
        when(dao.siguientePendiente()).thenReturn(revision);
        doAnswer(i->{ assertTrue(revision.pendiente()); return null; }).when(canal).waitForConfirmsOrDie(3000);
        try (var fabricas=mockConstruction(ConnectionFactory.class,
                (f,contexto)->when(f.newConnection("backoffice-reglas")).thenReturn(conexion))) {
            assertTrue(servicio.publicarSiguiente());
            assertEquals(1,fabricas.constructed().size());
        }
        assertFalse(revision.pendiente());
        var propiedades=ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        var cuerpo=ArgumentCaptor.forClass(byte[].class);
        verify(canal).basicPublish(eq(ConfiguracionReglas.EXCHANGE),eq(""),propiedades.capture(),cuerpo.capture());
        assertEquals("corr-1",propiedades.getValue().getCorrelationId());
        assertEquals("reglas-1",propiedades.getValue().getMessageId());
        assertEquals(2,propiedades.getValue().getDeliveryMode());
        try (var lector=Json.createReader(new StringReader(new String(cuerpo.getValue(),StandardCharsets.UTF_8)))) {
            var datos=lector.readObject();
            assertEquals(1,datos.getInt("schemaVersion"));
            assertEquals(1,datos.getInt("revision"));
            assertEquals(2000,datos.getInt("distanciaDesvioMetros"));
            assertEquals("SISTEMA",datos.getString("usuario"));
        }
        verify(canal).close(); verify(conexion).close();
    }

    @Test void unFalloDelBrokerDejaLaRevisionPendienteParaReintento() throws Exception {
        when(dao.siguientePendiente()).thenReturn(revision);
        doThrow(new IOException("sin confirmacion")).when(canal).waitForConfirmsOrDie(3000);
        try (var fabricas=mockConstruction(ConnectionFactory.class,
                (f,contexto)->when(f.newConnection("backoffice-reglas")).thenReturn(conexion))) {
            assertThrows(IOException.class,servicio::publicarSiguiente);
            assertEquals(1,fabricas.constructed().size());
        }
        assertTrue(revision.pendiente());
        verify(canal).close(); verify(conexion).close();
    }

    @Test void elPublicadorLimitaElLoteYConservaLosFallosParaReintento() throws Exception {
        var envio=mock(EnvioReglasService.class);
        var publicador=new PublicadorReglas();
        Field campo=PublicadorReglas.class.getDeclaredField("envio");campo.setAccessible(true);campo.set(publicador,envio);
        when(envio.publicarSiguiente()).thenReturn(true);
        publicador.publicar();
        verify(envio,times(10)).publicarSiguiente();
        reset(envio);
        when(envio.publicarSiguiente()).thenReturn(true,false);
        publicador.publicar();
        verify(envio,times(2)).publicarSiguiente();
        reset(envio);
        when(envio.publicarSiguiente()).thenThrow(new IOException("broker caido"));
        assertDoesNotThrow(publicador::publicar);
        verify(envio).publicarSiguiente();
    }
}
