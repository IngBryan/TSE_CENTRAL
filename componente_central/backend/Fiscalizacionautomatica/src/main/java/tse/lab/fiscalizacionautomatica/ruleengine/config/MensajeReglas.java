package tse.lab.fiscalizacionautomatica.ruleengine.config;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import tse.lab.reglas.ConfiguracionReglas;

public final class MensajeReglas {
    private static final ObjectMapper JSON=new ObjectMapper();
    private MensajeReglas() { }
    public static ConfiguracionReglas leer(byte[] body) throws IOException {
        if (body==null || body.length>65536) throw new IOException("Mensaje de configuración inválido");
        try {
            JsonNode n=JSON.readTree(body);
            if (entero(n,"schemaVersion")!=1) throw new IllegalArgumentException("Versión de mensaje no soportada");
            JsonNode revision=n.required("revision"), porcentaje=n.required("toleranciaCargaPorcentaje");
            if (!revision.isIntegralNumber() || !revision.canConvertToLong() || !porcentaje.isNumber())
                throw new IllegalArgumentException("Tipos numéricos inválidos");
            return new ConfiguracionReglas(revision.longValue(), porcentaje.decimalValue(),
                    entero(n,"toleranciaCargaKg"), entero(n,"distanciaDesvioMetros"), entero(n,"ventanaDesvioSegundos"),
                    texto(n,"fechaActualizacion"), texto(n,"usuario"), texto(n,"fundamento"), true);
        } catch (RuntimeException e) { throw new IOException("Configuración recibida inválida", e); }
    }
    private static int entero(JsonNode n, String key) {
        JsonNode v=n.required(key);
        if (!v.isIntegralNumber() || !v.canConvertToInt()) throw new IllegalArgumentException("Entero inválido: "+key);
        return v.intValue();
    }
    private static String texto(JsonNode n, String key) {
        JsonNode v=n.required(key);
        if (!v.isTextual()) throw new IllegalArgumentException("Texto inválido: "+key);
        return v.textValue();
    }
}
