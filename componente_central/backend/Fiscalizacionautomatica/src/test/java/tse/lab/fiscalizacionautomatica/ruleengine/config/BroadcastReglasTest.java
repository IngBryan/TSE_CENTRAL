package tse.lab.fiscalizacionautomatica.ruleengine.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;

/** Broker descartable, puerto 5673. No utiliza ni reinicia el broker del laboratorio. */
@EnabledIfEnvironmentVariable(named="REGLAS_RABBIT_INTEGRATION",matches="true")
class BroadcastReglasTest {
    @Test void dosInstanciasRecibenElCambioYUnaTerceraCargaLaVigente() throws Exception {
        ConnectionFactory f=new ConnectionFactory(); f.setHost("127.0.0.1"); f.setPort(5673);
        ReglasClient cliente=mock(ReglasClient.class);
        when(cliente.vigente()).thenReturn(EstadoReglasTest.c(1));
        when(cliente.revision(anyLong())).thenAnswer(i->EstadoReglasTest.c(i.getArgument(0)));
        EstadoReglas a=new EstadoReglas(cliente::revision), b=new EstadoReglas(cliente::revision), c=new EstadoReglas(cliente::revision);
        try (Connection ca=f.newConnection(); Connection cb=f.newConnection();
                SuscripcionReglas sa=new SuscripcionReglas(ca,cliente,a);
                SuscripcionReglas sb=new SuscripcionReglas(cb,cliente,b)) {
            sa.cargarInicial(new java.util.concurrent.CountDownLatch(1));
            sb.cargarInicial(new java.util.concurrent.CountDownLatch(1));
            when(cliente.vigente()).thenReturn(EstadoReglasTest.c(2));
            try (Channel p=ca.createChannel()) {
                String json="{\"schemaVersion\":1,\"revision\":2,\"toleranciaCargaPorcentaje\":2,\"toleranciaCargaKg\":0,"
                        +"\"distanciaDesvioMetros\":2000,\"ventanaDesvioSegundos\":30,\"fechaActualizacion\":\"2026-11-07T00:00:00Z\","
                        +"\"usuario\":\"admin\",\"fundamento\":\"prueba\"}";
                p.confirmSelect();
                p.basicPublish(tse.lab.reglas.ConfiguracionReglas.EXCHANGE,"",null,json.getBytes(StandardCharsets.UTF_8));
                p.waitForConfirmsOrDie(3000);
            }
            long hasta=System.nanoTime()+java.time.Duration.ofSeconds(5).toNanos();
            while ((a.actual().getRevision()!=2 || b.actual().getRevision()!=2) && System.nanoTime()<hasta) Thread.sleep(20);
            assertEquals(2,a.actual().getRevision()); assertEquals(2,b.actual().getRevision());
            try (Connection cc=f.newConnection(); SuscripcionReglas sc=new SuscripcionReglas(cc,cliente,c)) {
                sc.cargarInicial(new java.util.concurrent.CountDownLatch(1));
                assertEquals(2,c.actual().getRevision());
            }
        }
    }
}
