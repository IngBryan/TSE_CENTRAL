package tse.lab.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

// Un log de la plataforma: toString() lo devuelve como JSON en una linea, para seguir una operacion por su
// correlationId entre los componentes. Se usa siempre asi:
//   LOG.info(() -> LogCiudadanos.evento("guia.creada").correlationId(id).usuario(ci).campo("guia", g).toString());
// Los campos de aca son los que comparten todos los modulos; los propios de cada uno van con campo(), dentro de
// "datos". Cada modulo tiene su fabrica (LogCiudadanos, LogFuncionarios...) que fija el componente.
// Un campo en null no se escribe.
public final class LogEstructurado {

    private final Instant timestamp = Instant.now();
    private final String componente;
    private final String evento;
    private String correlationId;
    private String usuario;
    private Integer responseCode;
    private Double roundtripSeconds;
    private Double backendRoundtripSeconds;
    private String error;
    private String stack;
    private final Map<String, Object> datos = new LinkedHashMap<>();

    private LogEstructurado(String componente, String evento) {
        this.componente = componente;
        this.evento = evento;
    }

    public static LogEstructurado de(String componente, String evento) {
        return new LogEstructurado(componente, evento);
    }

    // Segundos desde un System.nanoTime(), para los roundtrip
    public static double segundosDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000_000d;
    }

    public LogEstructurado correlationId(String valor) {
        correlationId = valor;
        return this;
    }

    public LogEstructurado usuario(String valor) {
        usuario = valor;
        return this;
    }

    public LogEstructurado responseCode(int valor) {
        responseCode = valor;
        return this;
    }

    // Lo que tardo la operacion completa en este componente
    public LogEstructurado roundtripSeconds(double valor) {
        roundtripSeconds = valor;
        return this;
    }

    // Lo que tardo el servicio del que depende (el backend para un front, la PDI para el backend)
    public LogEstructurado backendRoundtripSeconds(double valor) {
        backendRoundtripSeconds = valor;
        return this;
    }

    public LogEstructurado error(Throwable e) {
        error = e.toString();
        StringWriter pila = new StringWriter();
        e.printStackTrace(new PrintWriter(pila));
        stack = pila.toString();
        return this;
    }

    public LogEstructurado campo(String nombre, Object valor) {
        datos.put(nombre, valor);
        return this;
    }

    @Override
    public String toString() {
        StringJoiner json = new StringJoiner(",", "{", "}");
        agregar(json, "timestamp", timestamp);
        agregar(json, "componente", componente);
        agregar(json, "evento", evento);
        agregar(json, "correlationId", correlationId);
        agregar(json, "usuario", usuario);
        agregar(json, "responseCode", responseCode);
        agregar(json, "roundtripSeconds", redondeado(roundtripSeconds));
        agregar(json, "backendRoundtripSeconds", redondeado(backendRoundtripSeconds));
        agregar(json, "error", error);
        agregar(json, "stack", stack);
        StringJoiner propios = new StringJoiner(",", "{", "}");
        datos.forEach((nombre, valor) -> agregar(propios, nombre, valor));
        if (propios.length() > 2) {
            json.add(texto("datos") + ":" + propios);
        }
        return json.toString();
    }

    private static void agregar(StringJoiner json, String nombre, Object valor) {
        if (valor != null) {
            json.add(texto(nombre) + ":" + valorJson(valor));
        }
    }

    private static String valorJson(Object valor) {
        boolean sinComillas = valor instanceof Boolean || valor instanceof RoundtripJson
                || valor instanceof Number n && Double.isFinite(n.doubleValue());
        return sinComillas ? valor.toString() : texto(valor.toString());
    }

    // Tres decimales, sin depender del Locale ni escribir notacion cientifica
    private record RoundtripJson(double segundos) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%.3f", segundos);
        }
    }

    private static RoundtripJson redondeado(Double segundos) {
        return segundos == null ? null : new RoundtripJson(segundos);
    }

    private static String texto(String valor) {
        StringBuilder json = new StringBuilder(valor.length() + 2).append('"');
        valor.chars().forEach(c -> {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", c));
                    } else {
                        json.append((char) c);
                    }
                }
            }
        });
        return json.append('"').toString();
    }
}
