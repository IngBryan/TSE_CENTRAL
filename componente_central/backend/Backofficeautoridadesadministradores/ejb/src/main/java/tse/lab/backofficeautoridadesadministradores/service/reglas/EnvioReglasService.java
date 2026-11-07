package tse.lab.backofficeautoridadesadministradores.service.reglas;

import com.rabbitmq.client.*;
import jakarta.ejb.*;
import jakarta.json.Json;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import tse.lab.backofficeautoridadesadministradores.data.ReglasDAO;
import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;
import tse.lab.reglas.ConfiguracionReglas;

@Stateless
public class EnvioReglasService {
    @EJB private ReglasDAO reglas;
    private static final Logger LOG=Logger.getLogger(EnvioReglasService.class.getName());
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean publicarSiguiente() throws Exception {
        var pendiente=reglas.siguientePendiente();
        if (pendiente == null) return false;
        var c=pendiente.datos();
        ConnectionFactory f=new ConnectionFactory();
        f.setHost(env("RABBITMQ_HOST", "localhost"));
        f.setPort(Integer.parseInt(env("RABBITMQ_PORT", "5672")));
        f.setUsername(env("RABBITMQ_USER", "guest"));
        f.setPassword(env("RABBITMQ_PASSWORD", "guest"));
        f.setConnectionTimeout(3000); f.setHandshakeTimeout(3000);
        f.setAutomaticRecoveryEnabled(false);
        try (Connection connection=f.newConnection("backoffice-reglas"); Channel canal=connection.createChannel()) {
            canal.exchangeDeclare(ConfiguracionReglas.EXCHANGE, "fanout", true);
            canal.confirmSelect();
            String json=Json.createObjectBuilder().add("schemaVersion", 1)
                    .add("revision", c.getRevision())
                    .add("toleranciaCargaPorcentaje", c.getToleranciaCargaPorcentaje())
                    .add("toleranciaCargaKg", c.getToleranciaCargaKg())
                    .add("distanciaDesvioMetros", c.getDistanciaDesvioMetros())
                    .add("ventanaDesvioSegundos", c.getVentanaDesvioSegundos())
                    .add("fechaActualizacion", c.getFechaActualizacion())
                    .add("usuario", c.getUsuario()).add("fundamento", c.getFundamento()).build().toString();
            var props=new AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(2)
                    .messageId("reglas-"+c.getRevision()).type("fiscalizacion.config.actualizada")
                    .correlationId(pendiente.getCorrelationId()).build();
            canal.basicPublish(ConfiguracionReglas.EXCHANGE, "", props, json.getBytes(StandardCharsets.UTF_8));
            canal.waitForConfirmsOrDie(3000);
            pendiente.publicada();
            LogBackoffice.evento("reglas.publicadas").campo("revision", c.getRevision())
                    .correlationId(pendiente.getCorrelationId()).escribirEn(LOG);
            return true;
        }
    }
    private static String env(String k, String value) {
        String v=System.getenv(k); return v == null || v.isBlank() ? value : v;
    }
}
