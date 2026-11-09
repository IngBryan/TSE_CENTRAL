package tse.lab.fiscalizacionautomatica.ruleengine.config;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.concurrent.*;
import tse.lab.reglas.ConfiguracionReglas;
import tse.lab.fiscalizacionautomatica.LogFiscalizacion;

/** Una cola por JVM, en otro canal de la conexión existente. PostgreSQL cubre períodos sin suscripción. */
public class SuscripcionReglas implements AutoCloseable {
    private final Connection connection;
    private final Channel channel;
    private final ReglasClient cliente;
    private final EstadoReglas estado;
    private final ScheduledExecutorService refresco=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"reglas-resync"); t.setDaemon(true); return t;
    });
    private final RecoveryListener recovery=new RecoveryListener() {
        public void handleRecoveryStarted(Recoverable r) { }
        public void handleRecovery(Recoverable r) { solicitarRefresco(); }
    };
    public SuscripcionReglas(Connection connection, ReglasClient cliente, EstadoReglas estado) throws IOException {
        this.connection=connection; this.cliente=cliente; this.estado=estado;
        channel=connection.createChannel();
        try {
            channel.exchangeDeclare(ConfiguracionReglas.EXCHANGE,"fanout",true);
            String cola=channel.queueDeclare("",false,true,true,null).getQueue();
            channel.queueBind(cola,ConfiguracionReglas.EXCHANGE,"");
            channel.basicQos(10);
            channel.basicConsume(cola,false,new DefaultConsumer(channel) {
                @Override public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties props, byte[] body)
                        throws IOException {
                    try { aplicar(MensajeReglas.leer(body)); }
                    catch (IOException | RuntimeException e) {
                        LogFiscalizacion.error(LogFiscalizacion.evento("reglas.mensaje_invalido").errorSinPila(e));
                        channel.basicReject(envelope.getDeliveryTag(),false);
                        solicitarRefresco(); return;
                    }
                    channel.basicAck(envelope.getDeliveryTag(),false);
                }
                @Override public void handleCancel(String tag) {
                    LogFiscalizacion.error(LogFiscalizacion.evento("reglas.suscripcion_cancelada"));
                    solicitarRefresco();
                }
            });
            if (connection instanceof Recoverable r) r.addRecoveryListener(recovery);
            refresco.scheduleWithFixedDelay(this::refrescar,60,60,TimeUnit.SECONDS);
        } catch (IOException | RuntimeException e) {
            close(); throw e;
        }
    }
    private void aplicar(ConfiguracionReglas c) {
        if (estado.aplicar(c)) LogFiscalizacion.info(LogFiscalizacion.evento("reglas.aplicadas")
                .campo("revision",c.getRevision()));
    }
    public void cargarInicial(CountDownLatch stop) throws InterruptedException {
        while (true) {
            refrescar();
            try { estado.actual(); return; } catch (IOException e) { /* aún sin snapshot */ }
            if (stop.await(5,TimeUnit.SECONDS)) return;
        }
    }
    private void solicitarRefresco() {
        if (!refresco.isShutdown()) try { refresco.execute(this::refrescar); }
        catch (RejectedExecutionException e) { /* cierre concurrente */ }
    }
    private void refrescar() {
        try { aplicar(cliente.vigente()); }
        catch (IOException | RuntimeException e) {
            LogFiscalizacion.error(LogFiscalizacion.evento("reglas.refresco_pendiente").errorSinPila(e));
        }
    }
    public void close() {
        if (connection instanceof Recoverable r) r.removeRecoveryListener(recovery);
        refresco.shutdownNow();
        try { channel.close(); } catch (Exception e) { /* cierre de conexión */ }
    }
}
