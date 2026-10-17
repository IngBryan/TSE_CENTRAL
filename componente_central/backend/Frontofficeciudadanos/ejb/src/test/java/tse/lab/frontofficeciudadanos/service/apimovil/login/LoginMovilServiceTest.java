package tse.lab.frontofficeciudadanos.service.apimovil.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ResultadoValidacion;
import tse.lab.frontofficeciudadanos.service.apimovil.login.ErrorLoginMovil.Motivo;
import tse.lab.frontofficeciudadanos.service.apimovil.login.LoginMovilService.ResultadoLoginMovil;
import tse.lab.frontofficeciudadanos.service.apimovil.login.TokenMovil.TokenInvalidoException;
import tse.lab.frontofficeciudadanos.service.autenticacion.IngresoService;
import tse.lab.frontofficeciudadanos.service.autenticacion.ResultadoIngreso;
import tse.lab.frontofficeciudadanos.service.autenticacion.RolPersona;
import tse.lab.model.Ciudadano;

// Login de la app movil contra gub.uy: canje (falso), validacion del id_token (firmado con una clave
// RSA de prueba), reglas de ingreso (IngresoService falso) y token de la app.
class LoginMovilServiceTest {

    private static final String CI = "56087406";
    private static final String SUB = "sub-gubuy-1";
    private static final String NONCE = "nonce-de-la-app";
    private static final String REDIRECT = "uy.carga.movil:/oauth2redirect";
    private static final byte[] SECRETO = "un-secreto-de-prueba-de-32-caracteres!!".getBytes(StandardCharsets.UTF_8);

    private static final ConfigGubUy CFG = new ConfigGubUy(
            "https://idp.prueba/oidc/v1", List.of("https://idp.prueba/oidc/v1"),
            "https://idp.prueba/oidc/v1/token", "https://idp.prueba/oidc/v1/userinfo", "https://idp.prueba/oidc/v1/jwks",
            "890192", "client-secret", List.of(REDIRECT, "http://localhost:8080"), Duration.ofSeconds(1));

    private KeyPair claves;
    private GubUyFalso gubUy;
    private IngresoFalso ingreso;
    private LoginMovilService servicio;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        claves = g.generateKeyPair();
        gubUy = new GubUyFalso();
        ingreso = new IngresoFalso();
        servicio = new LoginMovilService();
        servicio.cfg = CFG;
        servicio.gubUy = gubUy;
        servicio.secretoToken = SECRETO;
        Field f = LoginMovilService.class.getDeclaredField("ingreso");
        f.setAccessible(true);
        f.set(servicio, ingreso);
    }

    @Test
    void choferHabilitadoRecibeElTokenDeLaApp() throws Exception {
        ResultadoLoginMovil r = servicio.ingresar("code-1", "verifier-1", NONCE, REDIRECT, "corr-1");

        assertEquals(List.of("code-1", REDIRECT, "verifier-1"), gubUy.canje);
        assertEquals(CI, r.usuario().cedula());
        assertEquals("Bryan Perez", r.usuario().nombre());
        assertEquals(CI, TokenMovil.validar(r.token(), SECRETO, Instant.now()));
        assertEquals(CI, ingreso.cedula);
        assertEquals(SUB, ingreso.sub);
    }

    @Test
    void nonceDistintoNoIngresa() {
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", "verifier-1", "otro-nonce", REDIRECT, "corr"));
        assertEquals(Motivo.TOKEN_INVALIDO, e.getMotivo());
        assertNull(ingreso.cedula);
    }

    @Test
    void idTokenFirmadoConOtraClaveNoIngresa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        gubUy.firmante = g.generateKeyPair();
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", "verifier-1", NONCE, REDIRECT, "corr"));
        assertEquals(Motivo.TOKEN_INVALIDO, e.getMotivo());
    }

    @Test
    void idTokenParaOtroClienteNoIngresa() {
        gubUy.audiencia = "otro-cliente";
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", "verifier-1", NONCE, REDIRECT, "corr"));
        assertEquals(Motivo.TOKEN_INVALIDO, e.getMotivo());
    }

    @Test
    void redirectNoRegistradaSeRechazaSinLlamarAGubUy() {
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", "verifier-1", NONCE, "https://otro.sitio/cb", "corr"));
        assertEquals(Motivo.REDIRECT_INVALIDO, e.getMotivo());
        assertNull(gubUy.canje);
    }

    @Test
    void faltanDatos() {
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", null, NONCE, REDIRECT, "corr"));
        assertEquals(Motivo.SOLICITUD_INVALIDA, e.getMotivo());
    }

    @Test
    void noEsChoferHabilitado() {
        ingreso.roles = List.of(
                new RolPersona(RolPersona.Rol.RESPONSABLE, 1L, "Empresa", null),
                new RolPersona(RolPersona.Rol.CHOFER, 1L, "Empresa", "PENDIENTE_VALIDACION"));
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", "verifier-1", NONCE, REDIRECT, "corr"));
        assertEquals(Motivo.NO_CHOFER, e.getMotivo());
        assertEquals(403, e.getMotivo().status());
    }

    @Test
    void menorDeEdadSegunLaPdi() {
        ingreso.resultado = ResultadoValidacion.MENOR_DE_EDAD;
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", "verifier-1", NONCE, REDIRECT, "corr"));
        assertEquals(Motivo.MENOR_DE_EDAD, e.getMotivo());
    }

    @Test
    void gubUyNoDisponible() {
        gubUy.caido = true;
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", "verifier-1", NONCE, REDIRECT, "corr"));
        assertEquals(Motivo.IDP_NO_DISPONIBLE, e.getMotivo());
        assertEquals(503, e.getMotivo().status());
    }

    @Test
    void sinSecretConfiguradoNoAtiende() {
        servicio.secretoToken = new byte[0];
        ErrorLoginMovil e = assertThrows(ErrorLoginMovil.class,
                () -> servicio.ingresar("code-1", "verifier-1", NONCE, REDIRECT, "corr"));
        assertEquals(Motivo.INGRESO_NO_DISPONIBLE, e.getMotivo());
    }

    @Test
    void cedulaDeNumeroDocumentoOUid() {
        assertEquals("12345678", LoginMovilService.cedulaDe(Json.createObjectBuilder().add("numero_documento", "1.234.567-8").build()));
        assertEquals("56087406", LoginMovilService.cedulaDe(Json.createObjectBuilder().add("uid", "uy-ci-56087406").build()));
        assertNull(LoginMovilService.cedulaDe(Json.createObjectBuilder().add("uid", "otra-cosa").build()));
    }

    @Test
    void tokenDeLaAppVencidoAlterado() throws Exception {
        Instant ahora = Instant.parse("2026-10-17T12:00:00Z");
        String token = TokenMovil.emitir(CI, "Bryan", SECRETO, ahora).token();

        assertEquals(CI, TokenMovil.validar(token, SECRETO, ahora.plusSeconds(60)));
        TokenInvalidoException vencido = assertThrows(TokenInvalidoException.class,
                () -> TokenMovil.validar(token, SECRETO, ahora.plusSeconds(TokenMovil.DURACION_S + 1)));
        assertTrue(vencido.isVencido());

        byte[] otroSecreto = "otro-secreto-de-prueba-de-32-caracteres!".getBytes(StandardCharsets.UTF_8);
        assertThrows(TokenInvalidoException.class, () -> TokenMovil.validar(token, otroSecreto, ahora));
        String[] p = token.split("\\.");
        String payloadAlterado = Jwt.base64(Json.createObjectBuilder().add("sub", "99999999").add("iss", "carga.uy")
                .add("aud", "carga.uy-movil").add("exp", ahora.getEpochSecond() + 100).build().toString()
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(TokenInvalidoException.class,
                () -> TokenMovil.validar(p[0] + "." + payloadAlterado + "." + p[2], SECRETO, ahora));
        assertThrows(TokenInvalidoException.class, () -> TokenMovil.validar("basura", SECRETO, ahora));
    }

    // ID Uruguay falso: devuelve un id_token firmado con la clave de prueba y publica esa clave en el JWKS.
    private class GubUyFalso extends ClienteGubUy {
        List<String> canje;
        KeyPair firmante;
        String audiencia = CFG.clientId();
        boolean caido;

        GubUyFalso() {
            super(CFG);
        }

        @Override
        public Tokens canjear(String code, String redirectUri, String codeVerifier) throws ErrorLoginMovil {
            if (caido) {
                throw new ErrorLoginMovil(Motivo.IDP_NO_DISPONIBLE, "timeout");
            }
            canje = List.of(code, redirectUri, codeVerifier);
            return new Tokens(idToken(firmante == null ? claves : firmante), "access-token");
        }

        @Override
        public JsonArray jwks(boolean forzar) {
            RSAPublicKey pub = (RSAPublicKey) claves.getPublic();
            return Json.createArrayBuilder().add(Json.createObjectBuilder()
                    .add("kty", "RSA").add("kid", "k1").add("use", "sig")
                    .add("n", Jwt.base64(sinSigno(pub.getModulus())))
                    .add("e", Jwt.base64(sinSigno(pub.getPublicExponent())))).build();
        }

        @Override
        public JsonObject userinfo(String accessToken, String subEsperado) {
            return Json.createObjectBuilder().add("sub", SUB).add("uid", "uy-ci-" + CI)
                    .add("email", "chofer@prueba.uy").add("nombre_completo", "Nombre GubUy").build();
        }

        private String idToken(KeyPair kp) {
            try {
                long ahora = Instant.now().getEpochSecond();
                String h = Jwt.base64(Json.createObjectBuilder().add("alg", "RS256").add("kid", "k1").build()
                        .toString().getBytes(StandardCharsets.UTF_8));
                String p = Jwt.base64(Json.createObjectBuilder().add("iss", CFG.issuer()).add("aud", audiencia)
                        .add("sub", SUB).add("nonce", NONCE).add("iat", ahora).add("exp", ahora + 300).build()
                        .toString().getBytes(StandardCharsets.UTF_8));
                Signature s = Signature.getInstance("SHA256withRSA");
                s.initSign(kp.getPrivate());
                s.update((h + "." + p).getBytes(StandardCharsets.US_ASCII));
                return h + "." + p + "." + Jwt.base64(s.sign());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private byte[] sinSigno(BigInteger n) {
            byte[] b = n.toByteArray();
            return b[0] == 0 ? java.util.Arrays.copyOfRange(b, 1, b.length) : b;
        }
    }

    // Ingreso falso: PDI y roles configurables.
    private static class IngresoFalso extends IngresoService {
        String cedula;
        String sub;
        ResultadoValidacion resultado = ResultadoValidacion.VALIDADO;
        List<RolPersona> roles = List.of(new RolPersona(RolPersona.Rol.CHOFER, 1L, "Empresa", "HABILITADO"));

        @Override
        public ResultadoIngreso ingresarConGubUy(String cedula, String email, String subGubUy, String correlationId) {
            this.cedula = cedula;
            this.sub = subGubUy;
            Ciudadano p = new Ciudadano(cedula, email, subGubUy);
            p.setNombres("Bryan");
            p.setApellidos("Perez");
            boolean sesion = resultado == ResultadoValidacion.VALIDADO || resultado == ResultadoValidacion.NO_DISPONIBLE;
            return new ResultadoIngreso(p, resultado, false, sesion, sesion ? roles : List.of());
        }
    }
}
