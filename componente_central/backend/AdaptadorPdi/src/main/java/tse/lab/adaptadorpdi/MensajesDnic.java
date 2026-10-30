package tse.lab.adaptadorpdi;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

// Arma el request obtDocDigitalizado del Servicio de Informacion DNIC (con WS-Addressing y
// WS-Security UsernameToken PasswordText, como lo exige la PDI) e interpreta la respuesta.
// Sin estado: se prueba sin levantar nada.
final class MensajesDnic {

    static final String ACCION = "http://wsServicioDeInformacion/obtDocDigitalizado";
    static final int PERSONA_NO_ENCONTRADA = 1001;
    private static final String PASSWORD_TEXT =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

    private MensajesDnic() {
    }

    static String request(String url, String messageId, String organismo, String clave, String cedula,
            int idSolicitud, Instant ahora, long ttlSegundos) {
        Instant creado = ahora.truncatedTo(ChronoUnit.MILLIS);
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ws="http://wsServicioDeInformacion/"
                                  xmlns:wsa="http://www.w3.org/2005/08/addressing"
                                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                                  xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                  <soapenv:Header>
                    <wsa:Action>%s</wsa:Action>
                    <wsa:MessageID>%s</wsa:MessageID>
                    <wsa:To>%s</wsa:To>
                    <wsse:Security soapenv:mustUnderstand="1">
                      <wsu:Timestamp wsu:Id="TS-1">
                        <wsu:Created>%s</wsu:Created>
                        <wsu:Expires>%s</wsu:Expires>
                      </wsu:Timestamp>
                      <wsse:UsernameToken wsu:Id="UT-1">
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password Type="%s">%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>
                    <ws:obtDocDigitalizado>
                      <ws:paramObtDocDigitalizado>
                        <ws:TipoDocumento>DO</ws:TipoDocumento>
                        <ws:IdSolicitud>%d</ws:IdSolicitud>
                        <ws:Nrodocumento>%s</ws:Nrodocumento>
                        <ws:NroIdentificacion>0</ws:NroIdentificacion>
                      </ws:paramObtDocDigitalizado>
                    </ws:obtDocDigitalizado>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(
                xml(ACCION), xml(messageId), xml(url),
                creado, creado.plusSeconds(ttlSegundos),
                xml(organismo), PASSWORD_TEXT, xml(clave),
                idSolicitud, xml(cedula));
    }

    // Persona encontrada, vacio si DNIC responde 1001 (no existe o dada de baja).
    // Cualquier otra cosa (SOAP Fault, error de DNIC, XML ilegible) es PdiNoDisponibleException.
    static Optional<DatosPersonaPdi> respuesta(String cuerpo) throws PdiNoDisponibleException {
        Document doc = parsear(cuerpo);

        Element fault = primero(doc, "Fault");
        if (fault != null) {
            throw new PdiNoDisponibleException("SOAP Fault: " + texto(fault, "faultstring"));
        }

        Element codMensaje = primero(doc, "CodMensaje");
        if (codMensaje != null) {
            String codigo = codMensaje.getTextContent().trim();
            if (String.valueOf(PERSONA_NO_ENCONTRADA).equals(codigo)) {
                return Optional.empty();
            }
            throw new PdiNoDisponibleException("DNIC respondio el error " + codigo);
        }

        Element persona = primero(doc, "ObjPersona");
        if (persona == null) {
            throw new PdiNoDisponibleException("La respuesta no trae ObjPersona ni errores");
        }
        String fecha = texto(persona, "FechaNacimiento");
        try {
            return Optional.of(new DatosPersonaPdi(
                    texto(persona, "Nombre1"),
                    texto(persona, "primerApellido"),
                    unir(texto(persona, "Nombre1"), texto(persona, "Nombre2")),
                    unir(texto(persona, "primerApellido"), texto(persona, "segundoApellido")),
                    LocalDate.parse(fecha)));
        } catch (DateTimeParseException | NullPointerException e) {
            throw new PdiNoDisponibleException("FechaNacimiento invalida: " + fecha, e);
        }
    }

    private static Document parsear(String cuerpo) throws PdiNoDisponibleException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // Sin DTD ni entidades externas (XXE)
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler()); // sin "[Fatal Error]" por stderr: el error va en la excepcion
            return builder.parse(new ByteArrayInputStream(cuerpo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new PdiNoDisponibleException("Respuesta de la PDI ilegible", e);
        }
    }

    private static Element primero(Document doc, String nombreLocal) {
        NodeList nodos = doc.getElementsByTagNameNS("*", nombreLocal);
        return nodos.getLength() > 0 ? (Element) nodos.item(0) : null;
    }

    private static String texto(Element padre, String nombreLocal) {
        NodeList nodos = padre.getElementsByTagNameNS("*", nombreLocal);
        if (nodos.getLength() == 0) {
            nodos = padre.getElementsByTagName(nombreLocal); // faultstring no tiene namespace
        }
        if (nodos.getLength() == 0) {
            return null;
        }
        String valor = nodos.item(0).getTextContent().trim();
        return valor.isEmpty() ? null : valor;
    }

    private static String unir(String... partes) {
        return Stream.of(partes).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" "));
    }

    private static String xml(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
