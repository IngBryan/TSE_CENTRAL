package tse.lab.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.EstadoCaso;
import tse.lab.model.RegistroAuditoriaCaso;

class LogEstructuradoTest {

    @Test
    void escribeLosCamposComunesYLosDelModuloEnUnaLinea() {
        String log = LogEstructurado.de("ciudadanos", "guia.creada").correlationId("c-1").usuario("123")
                .responseCode(200).roundtripSeconds(0.1234).backendRoundtripSeconds(2)
                .campo("guia", "G1").campo("kg", 500).campo("foto", true).toString();

        assertTrue(log.contains("\"componente\":\"ciudadanos\",\"evento\":\"guia.creada\",\"correlationId\":\"c-1\","
                + "\"usuario\":\"123\",\"responseCode\":200,\"roundtripSeconds\":0.123,"
                + "\"backendRoundtripSeconds\":2.000,\"datos\":{\"guia\":\"G1\",\"kg\":500,\"foto\":true}}"), log);
        assertTrue(log.startsWith("{\"timestamp\":\""), log);
    }

    @Test
    void noEscribeLosCamposEnNull() {
        String log = LogEstructurado.de("c", "e").usuario(null).campo("x", null).toString();

        assertFalse(log.contains("usuario"), log);
        assertFalse(log.contains("datos"), log);
    }

    @Test
    void escapaLoQuePodriaRomperElJsonOFalsearOtraLinea() {
        String log = LogEstructurado.de("c", "e").campo("x", "a\"b\\c\nd\u0001").toString();

        assertTrue(log.contains("\"x\":\"a\\\"b\\\\c\\nd\\u0001\""), log);
        assertFalse(log.contains("\n"), log);
    }

    @Test
    void unNumeroNoFinitoVaComoTextoParaQueElJsonSeaValido() {
        assertTrue(LogEstructurado.de("c", "e").campo("x", Double.NaN).toString().contains("\"x\":\"NaN\""));
    }

    @Test
    void elErrorLlevaLaExcepcionYSuPilaEnUnaLinea() {
        String log = LogEstructurado.de("c", "e").error(new IllegalStateException("fallo")).toString();

        assertTrue(log.contains("\"error\":\"java.lang.IllegalStateException: fallo\""), log);
        assertTrue(log.contains("\"stack\":\""), log);
        assertFalse(log.contains("\n"), log);
    }

    @Test
    void writesTheLevelWithTheNamesOfTheFronts() {
        assertTrue(LogEstructurado.de("c", "e").toString().contains("\"nivel\":\"INFO\""));
        assertTrue(LogEstructurado.de("c", "e").nivel(Level.WARNING).toString().contains("\"nivel\":\"WARN\""));
        assertTrue(LogEstructurado.de("c", "e").nivel(Level.SEVERE).toString().contains("\"nivel\":\"ERROR\""));
    }

    @Test
    void errorWithoutStackKeepsTheCause() {
        String log = LogEstructurado.de("c", "e")
                .errorSinPila(new IllegalStateException("fallo", new RuntimeException("causa"))).toString();

        assertTrue(log.contains("\"error\":\"java.lang.IllegalStateException: fallo: java.lang.RuntimeException: causa\""),
                log);
        assertFalse(log.contains("\"stack\""), log);
    }

    @Test
    void anAccessLogIsAnErrorOnlyOnA5xx() {
        String ok = LogEstructurado.de("c", "http.request").http("GET", "/casos", 404, System.nanoTime()).toString();
        String fallo = LogEstructurado.de("c", "http.request").http("GET", "/casos", 500, System.nanoTime()).toString();

        assertTrue(ok.contains("\"nivel\":\"INFO\""), ok);
        assertTrue(ok.contains("\"responseCode\":404"), ok);
        assertTrue(ok.contains("\"roundtripSeconds\":"), ok);
        assertTrue(ok.contains("\"datos\":{\"metodo\":\"GET\",\"ruta\":\"/casos\"}"), ok);
        assertTrue(fallo.contains("\"nivel\":\"ERROR\""), fallo);
    }

    @Test
    void writesWithItsOwnLevel() {
        Logger logger = Logger.getLogger("tse.lab.prueba");

        List<LogRecord> lineas = capturar(logger,
                () -> LogEstructurado.de("c", "e").nivel(Level.WARNING).escribirEn(logger));

        assertEquals(Level.WARNING, lineas.get(0).getLevel());
        assertTrue(lineas.get(0).getMessage().contains("\"evento\":\"e\""));
    }

    @Test
    void laPistaDeAuditoriaDiceQuienQueConQueFundamentoYCuando() {
        List<LogRecord> lineas = capturar(Logger.getLogger("tse.lab.auditoria"),
                () -> transaccion(Status.STATUS_COMMITTED));

        assertEquals(1, lineas.size());
        String log = lineas.get(0).getMessage();
        assertTrue(log.contains("\"evento\":\"auditoria.caso\",\"correlationId\":\"c-9\",\"usuario\":\"123\""), log);
        assertTrue(log.contains("\"caso\":7,\"accion\":\"RESOLUCION\",\"estadoAnterior\":\"EN_DESCARGO\","
                + "\"estadoNuevo\":\"CONFIRMADA\",\"fundamento\":\"Exceso de peso\",\"fechaHora\":"), log);
    }

    @Test
    void aRolledBackActionLeavesNoAuditLine() {
        assertTrue(capturar(Logger.getLogger("tse.lab.auditoria"), () -> transaccion(Status.STATUS_ROLLEDBACK)).isEmpty());
    }

    // Ends a transaction with that outcome, as the container does with the registered Synchronization
    private static void transaccion(int estado) {
        List<Synchronization> registradas = new ArrayList<>();
        TransactionSynchronizationRegistry registro = (TransactionSynchronizationRegistry) Proxy.newProxyInstance(
                LogEstructuradoTest.class.getClassLoader(), new Class<?>[] { TransactionSynchronizationRegistry.class },
                (proxy, metodo, argumentos) -> {
                    registradas.add((Synchronization) argumentos[0]);
                    return null;
                });
        PistaAuditoria.alConfirmar(registro, "funcionarios", new RegistroAuditoriaCaso(7L, "123",
                AccionAuditoriaCaso.RESOLUCION, EstadoCaso.EN_DESCARGO, EstadoCaso.CONFIRMADA, "Exceso de peso", "c-9"));
        registradas.get(0).beforeCompletion();
        registradas.get(0).afterCompletion(estado);
    }

    private static List<LogRecord> capturar(Logger logger, Runnable accion) {
        List<LogRecord> lineas = new ArrayList<>();
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord r) {
                lineas.add(r);
            }

            @Override
            public void flush() {
                // Nothing buffered
            }

            @Override
            public void close() {
                // Nothing to release
            }
        };
        logger.addHandler(captura);
        try {
            accion.run();
        } finally {
            logger.removeHandler(captura);
        }
        return lineas;
    }
}
