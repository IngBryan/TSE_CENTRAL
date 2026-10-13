package tse.lab.frontofficeciudadanos.service.adaptadorpdi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class MensajesDnicTest {

    private static String respuesta(String resultado) {
        return """
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                  <SOAP-ENV:Body>
                    <ns2:obtDocDigitalizadoResponse xmlns:ns2="http://wsServicioDeInformacion/">
                      <ns2:obtDocDigitalizadoResult>%s</ns2:obtDocDigitalizadoResult>
                    </ns2:obtDocDigitalizadoResponse>
                  </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>""".formatted(resultado);
    }

    @Test
    void requestLlevaAddressingSecurityYCedula() {
        String xml = MensajesDnic.request("http://pdi/ws", "urn:uuid:1", "ORG", "cl<ave", "11111111", 7,
                Instant.parse("2026-01-01T00:00:00Z"), 300);
        assertTrue(xml.contains("<wsa:Action>http://wsServicioDeInformacion/obtDocDigitalizado</wsa:Action>"));
        assertTrue(xml.contains("<wsa:MessageID>urn:uuid:1</wsa:MessageID>"));
        assertTrue(xml.contains("<wsu:Expires>2026-01-01T00:05:00Z</wsu:Expires>"));
        assertTrue(xml.contains("<wsse:Username>ORG</wsse:Username>"));
        assertTrue(xml.contains(">cl&lt;ave</wsse:Password>"));
        assertTrue(xml.contains("<ws:Nrodocumento>11111111</ws:Nrodocumento>"));
    }

    @Test
    void personaEncontrada() throws Exception {
        Optional<DatosPersonaPdi> p = MensajesDnic.respuesta(respuesta("""
                <ns2:idSolicitud>7</ns2:idSolicitud><ns2:idRespuesta>1</ns2:idRespuesta>
                <ns2:ObjPersona>
                  <ns2:primerApellido>Pérez</ns2:primerApellido><ns2:segundoApellido>García</ns2:segundoApellido>
                  <ns2:Nombre1>Juan</ns2:Nombre1><ns2:Nombre2>Carlos</ns2:Nombre2>
                  <ns2:FechaNacimiento>1980-05-10</ns2:FechaNacimiento>
                </ns2:ObjPersona>"""));
        assertEquals(new DatosPersonaPdi("Juan", "Pérez", "Juan Carlos", "Pérez García", LocalDate.of(1980, 5, 10)), p.orElseThrow());
    }

    @Test
    void personaNoEncontrada() throws Exception {
        assertFalse(MensajesDnic.respuesta(respuesta("""
                <ns2:Errores><ns2:Mensaje><ns2:CodMensaje>1001</ns2:CodMensaje></ns2:Mensaje></ns2:Errores>""")).isPresent());
    }

    @Test
    void otroErrorDeDnicOFaultEsNoDisponible() {
        assertThrows(PdiNoDisponibleException.class, () -> MensajesDnic.respuesta(respuesta("""
                <ns2:Errores><ns2:Mensaje><ns2:CodMensaje>3001</ns2:CodMensaje></ns2:Mensaje></ns2:Errores>""")));
        assertThrows(PdiNoDisponibleException.class, () -> MensajesDnic.respuesta("""
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"><SOAP-ENV:Body>
                <SOAP-ENV:Fault><faultcode>SOAP-ENV:Server</faultcode><faultstring>PDI caida</faultstring></SOAP-ENV:Fault>
                </SOAP-ENV:Body></SOAP-ENV:Envelope>"""));
        assertThrows(PdiNoDisponibleException.class, () -> MensajesDnic.respuesta("<html>502</html"));
    }

    @Test
    void mayoriaDeEdadA18() {
        LocalDate hoy = LocalDate.of(2026, 10, 10);
        assertTrue(MayoriaDeEdad.cumple(LocalDate.of(2008, 10, 10), hoy));
        assertFalse(MayoriaDeEdad.cumple(LocalDate.of(2008, 10, 11), hoy));
    }
}
