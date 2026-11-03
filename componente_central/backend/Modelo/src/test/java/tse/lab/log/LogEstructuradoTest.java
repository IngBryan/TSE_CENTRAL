package tse.lab.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

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
    void laPistaDeAuditoriaDiceQuienQueConQueFundamentoYCuando() {
        List<LogRecord> lineas = new ArrayList<>();
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord r) {
                lineas.add(r);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger("tse.lab.auditoria");
        logger.addHandler(captura);
        try {
            PistaAuditoria.registrar("funcionarios", new RegistroAuditoriaCaso(7L, "123", AccionAuditoriaCaso.RESOLUCION,
                    EstadoCaso.EN_DESCARGO, EstadoCaso.CONFIRMADA, "Exceso de peso", "c-9"));
        } finally {
            logger.removeHandler(captura);
        }

        assertEquals(1, lineas.size());
        String log = lineas.get(0).getMessage();
        assertTrue(log.contains("\"evento\":\"auditoria.caso\",\"correlationId\":\"c-9\",\"usuario\":\"123\""), log);
        assertTrue(log.contains("\"caso\":7,\"accion\":\"RESOLUCION\",\"estadoAnterior\":\"EN_DESCARGO\","
                + "\"estadoNuevo\":\"CONFIRMADA\",\"fundamento\":\"Exceso de peso\",\"fechaHora\":"), log);
    }
}
