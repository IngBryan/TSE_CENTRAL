package tse.lab.fiscalizacionautomatica.ruleengine.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SuscripcionReglasTest {
    private final Connection conexion=mock(Connection.class,withSettings().extraInterfaces(Recoverable.class));
    private final Channel canal=mock(Channel.class);
    private final ReglasClient cliente=mock(ReglasClient.class);
    private final CountDownLatch actualizada=new CountDownLatch(1);
    private final EstadoReglas estado=new EstadoReglas(EstadoReglasTest::c) {
        @Override public boolean aplicar(tse.lab.reglas.ConfiguracionReglas c) {
            boolean aplicada=super.aplicar(c);
            if (c.getRevision()==3) actualizada.countDown();
            return aplicada;
        }
    };

    @BeforeEach void preparar() throws Exception {
        when(conexion.createChannel()).thenReturn(canal);
        var declarada=mock(AMQP.Queue.DeclareOk.class);
        when(declarada.getQueue()).thenReturn("cola");
        when(canal.queueDeclare("",false,true,true,null)).thenReturn(declarada);
        when(cliente.vigente()).thenReturn(EstadoReglasTest.c(1));
    }
    @Test void aplicaElBroadcastAntesDeConfirmarloYDescartaLosMensajesInvalidos() throws Exception {
        try (var suscripcion=new SuscripcionReglas(conexion,cliente,estado)) {
            var captor=ArgumentCaptor.forClass(Consumer.class);
            verify(canal).basicConsume(eq("cola"),eq(false),captor.capture());
            var consumidor=captor.getValue();
            doAnswer(i->{assertEquals(2,estado.actual().getRevision());return null;}).when(canal).basicAck(1,false);
            consumidor.handleDelivery("t",new Envelope(1,false,"",""),null,MensajeReglasTest.mensaje(2));
            verify(canal).basicAck(1,false);
            consumidor.handleDelivery("t",new Envelope(2,false,"",""),null,new byte[]{'{'});
            verify(canal).basicReject(2,false);
            verify(canal,never()).basicAck(2,false);
            verify(cliente,timeout(1000).atLeastOnce()).vigente();
            assertEquals(2,estado.actual().getRevision());
        }
        verify(canal).close();
        verify((Recoverable)conexion).removeRecoveryListener(any());
    }
    @Test void bootstrapYRecuperacionConsultanLaRevisionPersistida() throws Exception {
        try (var suscripcion=new SuscripcionReglas(conexion,cliente,estado)) {
            suscripcion.cargarInicial(new CountDownLatch(1));
            assertEquals(1,estado.actual().getRevision());
            var captor=ArgumentCaptor.forClass(RecoveryListener.class);
            verify((Recoverable)conexion).addRecoveryListener(captor.capture());
            when(cliente.vigente()).thenReturn(EstadoReglasTest.c(3));
            captor.getValue().handleRecoveryStarted((Recoverable)conexion);
            captor.getValue().handleRecovery((Recoverable)conexion);
            assertTrue(actualizada.await(1,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(3,estado.actual().getRevision());
    }
    @Test void unaCancelacionSolicitaRefrescoYElBootstrapPuedeDetenerseSinSnapshot() throws Exception {
        when(cliente.vigente()).thenThrow(new IOException("sin servidor"));
        try (var suscripcion=new SuscripcionReglas(conexion,cliente,estado)) {
            suscripcion.cargarInicial(new CountDownLatch(0));
            assertThrows(IOException.class,estado::actual);
            var captor=ArgumentCaptor.forClass(Consumer.class);
            verify(canal).basicConsume(eq("cola"),eq(false),captor.capture());
            captor.getValue().handleCancel("t");
            verify(cliente,timeout(1000).atLeast(2)).vigente();
        }
    }
    @Test void unFalloAlDeclararLaSuscripcionCierraElCanal() throws Exception {
        doThrow(new IOException("sin exchange")).when(canal).exchangeDeclare(anyString(),eq("fanout"),eq(true));
        assertThrows(IOException.class,()->new SuscripcionReglas(conexion,cliente,estado));
        verify(canal).close();
    }
    @Test void bootstrapReintentaAntesDeHabilitarLasEvaluaciones() throws Exception {
        when(cliente.vigente()).thenThrow(new IOException("sin servidor")).thenReturn(EstadoReglasTest.c(1));
        var stop=mock(CountDownLatch.class);
        when(stop.await(5,java.util.concurrent.TimeUnit.SECONDS)).thenReturn(false);
        try (var suscripcion=new SuscripcionReglas(conexion,cliente,estado)) {
            suscripcion.cargarInicial(stop);
            assertEquals(1,estado.actual().getRevision());
            verify(stop).await(5,java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    @Test void cerrarSinRecuperacionAutomaticaToleraUnCanalYaCerrado() throws Exception {
        var simple=mock(Connection.class);
        when(simple.createChannel()).thenReturn(canal);
        var suscripcion=new SuscripcionReglas(simple,cliente,estado);
        doThrow(new IOException("canal cerrado")).when(canal).close();
        assertDoesNotThrow(suscripcion::close);
        verify(canal).close();
    }

}
