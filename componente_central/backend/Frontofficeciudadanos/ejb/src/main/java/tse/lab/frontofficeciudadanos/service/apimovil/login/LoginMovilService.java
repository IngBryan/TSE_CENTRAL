package tse.lab.frontofficeciudadanos.service.apimovil.login;

import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.json.JsonObject;

import tse.lab.frontofficeciudadanos.service.LogCiudadanos;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ResultadoValidacion;
import tse.lab.frontofficeciudadanos.service.apimovil.login.ErrorLoginMovil.Motivo;
import tse.lab.frontofficeciudadanos.service.autenticacion.IngresoService;
import tse.lab.frontofficeciudadanos.service.autenticacion.ResultadoIngreso;
import tse.lab.frontofficeciudadanos.service.autenticacion.RolPersona;

// Login de la app movil con Usuario gub.uy, sin pasar por el frontoffice de Next:
// 1. La app hace el authorize contra ID Uruguay (Custom Tab, PKCE S256, state y nonce propios) y
//    recibe el code en su redirect URI (uy.carga.movil:/oauth2redirect o el loopback http://localhost:8080).
// 2. La app manda code + code_verifier + nonce + redirect_uri a POST /api/movil/auth/token.
// 3. Aca se canjea el code con el client secret (que nunca esta en el APK), se valida el id_token
//    (firma, iss, aud, exp y el nonce de la app) y se piden los datos a UserInfo.
// 4. Se aplica el mismo ingreso que la web (CU-01: perfil, validacion contra la PDI, roles) y se exige
//    que sea CHOFER HABILITADO en alguna empresa.
// 5. Se emite el token de la app (TokenMovil, 8 h).
//
// Sin transaccion propia: las llamadas a gub.uy no tienen que quedar dentro de una transaccion de la
// base. IngresoService abre la suya.
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class LoginMovilService {

    private static final Logger LOG = Logger.getLogger(LoginMovilService.class.getName());
    private static final Pattern UID_CI = Pattern.compile("^uy-ci-(\\d+)$", Pattern.CASE_INSENSITIVE);

    @EJB
    private IngresoService ingreso;

    // Se pueden reemplazar en los tests
    ConfigGubUy cfg = ConfigGubUy.desdeEntorno();
    ClienteGubUy gubUy = new ClienteGubUy(cfg);
    byte[] secretoToken = TokenMovil.secretoConfigurado();

    public record UsuarioMovil(String cedula, String nombre, List<RolPersona> roles) {
    }

    public record ResultadoLoginMovil(String token, long expira, UsuarioMovil usuario) {
    }

    public ResultadoLoginMovil ingresar(String code, String codeVerifier, String nonce, String redirectUri,
            String correlationId) throws ErrorLoginMovil {
        if (Stream.of(code, codeVerifier, nonce, redirectUri).anyMatch(s -> s == null || s.isBlank())) {
            throw fallar(new ErrorLoginMovil(Motivo.SOLICITUD_INVALIDA, "code, codeVerifier, nonce y redirectUri son obligatorios"),
                    correlationId);
        }
        if (!cfg.redirectUrisMovil().contains(redirectUri)) {
            throw fallar(new ErrorLoginMovil(Motivo.REDIRECT_INVALIDO, "redirect_uri no permitida: " + redirectUri), correlationId);
        }
        if (!cfg.completa() || secretoToken.length == 0) {
            throw fallar(new ErrorLoginMovil(Motivo.INGRESO_NO_DISPONIBLE,
                    "falta OIDC_CLIENT_ID, OIDC_CLIENT_SECRET o MOVIL_TOKEN_SECRET (32+ caracteres)"), correlationId);
        }

        try {
            ClienteGubUy.Tokens tokens = gubUy.canjear(code, redirectUri, codeVerifier);
            JsonObject idToken = VerificadorIdToken.verificar(tokens.idToken(), nonce, cfg, gubUy::jwks, Instant.now());
            JsonObject claims = gubUy.userinfo(tokens.accessToken(), idToken.getString("sub"));
            String cedula = cedulaDe(claims);
            if (cedula == null) {
                throw new ErrorLoginMovil(Motivo.TOKEN_INVALIDO,
                        "gub.uy no devolvio la cedula (falta el scope document o personal_info)");
            }

            ResultadoIngreso r = ingreso.ingresarConGubUy(cedula, claims.getString("email", null),
                    idToken.getString("sub"), correlationId);
            if (!r.sesionPermitida()) {
                throw new ErrorLoginMovil(r.resultado() == ResultadoValidacion.MENOR_DE_EDAD ? Motivo.MENOR_DE_EDAD : Motivo.NO_EXISTE,
                        "resultado PDI " + r.resultado());
            }
            if (r.roles().stream().noneMatch(LoginMovilService::esChoferHabilitado)) {
                throw new ErrorLoginMovil(Motivo.NO_CHOFER, "ci=" + cedula + " no es chofer habilitado");
            }

            String nombre = nombre(r, claims);
            TokenMovil.Emitido t = TokenMovil.emitir(cedula, nombre, secretoToken, Instant.now());
            LOG.info(() -> LogCiudadanos.evento("movil.login").correlationId(correlationId).campo("ci", cedula)
                    .campo("resultado", "ok").toString());
            return new ResultadoLoginMovil(t.token(), t.expira(), new UsuarioMovil(cedula, nombre, r.roles()));
        } catch (ErrorLoginMovil e) {
            throw fallar(e, correlationId);
        }
    }

    static boolean esChoferHabilitado(RolPersona r) {
        return r.rol() == RolPersona.Rol.CHOFER && "HABILITADO".equals(r.estado());
    }

    // La cedula sale de numero_documento (scope document) o, si no vino, del uid (scope personal_info),
    // que tiene la forma "uy-ci-12345678".
    static String cedulaDe(JsonObject claims) {
        String doc = claims.getString("numero_documento", null);
        if (doc != null && !doc.replaceAll("\\D", "").isEmpty()) {
            return doc.replaceAll("\\D", "");
        }
        Matcher m = UID_CI.matcher(claims.getString("uid", ""));
        return m.matches() ? m.group(1) : null;
    }

    private static String nombre(ResultadoIngreso r, JsonObject claims) {
        String pdi = Stream.of(r.perfil().getNombres(), r.perfil().getApellidos())
                .filter(s -> s != null && !s.isBlank()).reduce((a, b) -> a + " " + b).orElse("");
        if (!pdi.isBlank()) {
            return pdi;
        }
        String completo = claims.getString("nombre_completo", "");
        if (!completo.isBlank()) {
            return completo;
        }
        return Stream.of("primer_nombre", "segundo_nombre", "primer_apellido", "segundo_apellido")
                .map(k -> claims.getString(k, "")).filter(s -> !s.isBlank())
                .reduce((a, b) -> a + " " + b).orElse("Chofer");
    }

    private static ErrorLoginMovil fallar(ErrorLoginMovil e, String correlationId) {
        LOG.warning(() -> LogCiudadanos.aviso("movil.login").correlationId(correlationId)
                .campo("resultado", e.getMotivo().codigo()).campo("detalle", "\"" + e.getMessage() + "\"")
                .toString());
        return e;
    }
}
