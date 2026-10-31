package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// Datos del caso de fiscalizacion, su descargo y resolucion, y las pistas de auditoria.
class CasoYAuditoriaTest {

    private final Empresa empresa = new Empresa();
    private final Vehiculo vehiculo = new Vehiculo("SBC1234", "Volvo", "FH", 9_000L, 25_000L, empresa);

    @Test
    void elCasoConGuiaTomaElVehiculoDeLaGuia() {
        Guia guia = new Guia("GV-1", "Agro", 18_000L, LocalDate.of(2026, 10, 19), "Montevideo", "Salto", empresa);
        guia.setVehiculo(vehiculo);

        CasoFiscalizacion caso = new CasoFiscalizacion("Exceso de carga", "EXCESO_CARGA", guia);

        assertNull(caso.getId());
        assertSame(guia, caso.getGuia());
        assertSame(vehiculo, caso.getVehiculo());
        assertTrue(caso.getEventosPesajes().isEmpty());
        assertTrue(caso.getClavesEventosTracking().isEmpty());
    }

    @Test
    void elCasoSinGuiaGuardaElVehiculo() {
        CasoFiscalizacion caso = new CasoFiscalizacion("Viaje sin guia", "VIAJE_SIN_GUIA", vehiculo, null);

        assertSame(vehiculo, caso.getVehiculo());
        assertNull(caso.getGuia());
        assertTrue(caso.getEstado().esAbierto());
        assertFalse(EstadoCaso.CONFIRMADA.esAbierto());
    }

    @Test
    void losDatosDelCasoSePuedenEditar() {
        CasoFiscalizacion caso = new CasoFiscalizacion();
        Guia guia = new Guia();
        Descargo descargo = new Descargo();
        FundamentoResolucion fundamento = new FundamentoResolucion();

        caso.setDescripcion("Desvio");
        caso.setTipoCaso("DESVIO_RUTA");
        caso.setGuia(guia);
        caso.setDescargo(descargo);
        caso.setFundamentoResolucion(fundamento);

        assertEquals("Desvio", caso.getDescripcion());
        assertEquals("DESVIO_RUTA", caso.getTipoCaso());
        assertSame(guia, caso.getGuia());
        assertSame(descargo, caso.getDescargo());
        assertSame(fundamento, caso.getFundamentoResolucion());
        // sin estado (constructor de JPA) no hay transicion posible
        assertThrows(IllegalStateException.class, () -> caso.cambiarEstado(EstadoCaso.NOTIFICADA));
    }

    @Test
    void presentarUnDescargoGuardaCuandoYQuienLoPresento() {
        CasoFiscalizacion caso = new CasoFiscalizacion("d", "EXCESO_CARGA", vehiculo, null);
        Instant antes = Instant.now();

        Descargo descargo = Descargo.presentar("La ruta estaba cortada", caso, "54074380");

        assertEquals("La ruta estaba cortada", descargo.getDescripcion());
        assertEquals("EXCESO_CARGA", descargo.getTipoCaso());
        assertEquals(Descargo.PRESENTADO, descargo.getEstado());
        assertSame(caso, descargo.getCasoFiscalizacion());
        assertEquals("54074380", descargo.getCiPresentante());
        assertFalse(descargo.getFechaPresentacion().isBefore(antes));
    }

    @Test
    void elDescargoMantieneSusDatos() {
        CasoFiscalizacion caso = new CasoFiscalizacion("d", "EXCESO_CARGA", null);
        Descargo descargo = new Descargo("La balanza estaba descalibrada", "EXCESO_CARGA", "PRESENTADO", caso);

        assertNull(descargo.getId());
        assertEquals("La balanza estaba descalibrada", descargo.getDescripcion());
        assertEquals("EXCESO_CARGA", descargo.getTipoCaso());
        assertEquals("PRESENTADO", descargo.getEstado());
        assertSame(caso, descargo.getCasoFiscalizacion());

        CasoFiscalizacion otro = new CasoFiscalizacion();
        descargo.setDescripcion("Otra");
        descargo.setTipoCaso("DESVIO_RUTA");
        descargo.setEstado("ACEPTADO");
        descargo.setCasoFiscalizacion(otro);

        assertEquals("Otra", descargo.getDescripcion());
        assertEquals("DESVIO_RUTA", descargo.getTipoCaso());
        assertEquals("ACEPTADO", descargo.getEstado());
        assertSame(otro, descargo.getCasoFiscalizacion());
    }

    @Test
    void elFundamentoGuardaFuncionarioCasoYFecha() {
        Funcionario funcionario = new Funcionario("33333333", "funcionario@example.com");
        CasoFiscalizacion caso = new CasoFiscalizacion("d", "EXCESO_CARGA", null);

        FundamentoResolucion fundamento = new FundamentoResolucion("Se confirma", funcionario, caso);

        assertNull(fundamento.getId());
        assertNotNull(fundamento.getFechaHora());
        assertEquals("Se confirma", fundamento.getDescripcion());
        assertSame(funcionario, fundamento.getFuncionario());
        assertSame(caso, fundamento.getCasoFiscalizacion());

        Funcionario otro = new Funcionario();
        CasoFiscalizacion otroCaso = new CasoFiscalizacion();
        fundamento.setDescripcion("Se desestima");
        fundamento.setFuncionario(otro);
        fundamento.setCasoFiscalizacion(otroCaso);

        assertEquals("Se desestima", fundamento.getDescripcion());
        assertSame(otro, fundamento.getFuncionario());
        assertSame(otroCaso, fundamento.getCasoFiscalizacion());
    }

    @Test
    void elFuncionarioMantieneSusDatos() {
        Funcionario funcionario = new Funcionario("33333333", "funcionario@example.com");

        assertEquals("33333333", funcionario.getCi());
        assertEquals("funcionario@example.com", funcionario.getEmail());
        assertTrue(funcionario.getFundamentosResolucion().isEmpty());

        funcionario.setCi("44444444");
        funcionario.setEmail("otro@example.com");

        assertEquals("44444444", funcionario.getCi());
        assertEquals("otro@example.com", funcionario.getEmail());
    }

    @Test
    void laAuditoriaDeCasosEsDeSoloAgregado() {
        RegistroAuditoriaCaso registro = new RegistroAuditoriaCaso(4L, RegistroAuditoriaCaso.SISTEMA,
                AccionAuditoriaCaso.CAMBIO_ESTADO, EstadoCaso.DETECTADA, EstadoCaso.NOTIFICADA, null, "corr-1");

        assertNull(registro.getId());
        assertEquals(4L, registro.getIdCaso());
        assertEquals("SISTEMA", registro.getUsuario());
        assertEquals(AccionAuditoriaCaso.CAMBIO_ESTADO, registro.getAccion());
        assertEquals(EstadoCaso.DETECTADA, registro.getEstadoAnterior());
        assertEquals(EstadoCaso.NOTIFICADA, registro.getEstadoNuevo());
        assertNull(registro.getFundamento());
        assertNotNull(registro.getFechaHora());
        assertEquals("corr-1", registro.getCorrelationId());
        assertThrows(UnsupportedOperationException.class, registro::soloAgregado);
        assertNull(new RegistroAuditoriaCaso().getIdCaso());
    }

    @Test
    void laConsultaDePersonaQuedaRegistrada() {
        RegistroConsultaPersona registro = new RegistroConsultaPersona("33333333", "11111111", "CONSULTA_PDI", "OK",
                "corr-2");

        assertNull(registro.getId());
        assertEquals("33333333", registro.getCiUsuario());
        assertEquals("11111111", registro.getCiConsultada());
        assertEquals("CONSULTA_PDI", registro.getOperacion());
        assertEquals("OK", registro.getResultado());
        assertEquals("corr-2", registro.getCorrelationId());
        assertFalse(registro.getFechaHora().isAfter(Instant.now()));
        assertNull(new RegistroConsultaPersona().getCiUsuario());
    }
}
