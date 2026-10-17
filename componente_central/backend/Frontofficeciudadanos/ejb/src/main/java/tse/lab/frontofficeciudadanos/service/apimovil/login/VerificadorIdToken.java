package tse.lab.frontofficeciudadanos.service.apimovil.login;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.function.Function;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import tse.lab.frontofficeciudadanos.service.apimovil.login.ErrorLoginMovil.Motivo;

// Valida el id_token que devuelve ID Uruguay: firma (RS256 contra el JWKS, o HS256 con el client secret),
// iss, aud, exp, nbf y el nonce que genero la app. Usa java.security directo: el ambiente de testing
// firma con una clave RSA de 1024 bits, que muchas librerias JWT rechazan.
public final class VerificadorIdToken {

    private static final long TOLERANCIA_S = 60;

    private VerificadorIdToken() {
    }

    // jwks.apply(false) devuelve las claves (cacheadas); jwks.apply(true) las vuelve a pedir, por si el
    // IdP roto la clave y el kid todavia no esta en la cache.
    public static JsonObject verificar(String idToken, String nonceEsperado, ConfigGubUy cfg,
            Function<Boolean, JsonArray> jwks, Instant ahora) throws ErrorLoginMovil {
        Jwt jwt;
        try {
            jwt = Jwt.leer(idToken);
        } catch (RuntimeException e) {
            throw invalido("id_token mal formado");
        }

        String alg = jwt.header().getString("alg", "");
        switch (alg) {
            case "RS256" -> verificarRs256(jwt, jwks, cfg.bitsMinimosRsa());
            case "HS256" -> {
                if (!MessageDigest.isEqual(hmac(cfg.clientSecret(), jwt.contenidoFirmado()), jwt.firma())) {
                    throw invalido("firma HS256 invalida");
                }
            }
            default -> throw invalido("alg no soportado: " + alg);
        }

        JsonObject p = jwt.payload();
        String iss = p.getString("iss", null);
        if (iss == null || !cfg.issuersAceptados().contains(iss)) {
            throw invalido("iss inesperado: " + iss);
        }
        if (!audienciaIncluye(p.get("aud"), cfg.clientId())) {
            throw invalido("aud inesperado: " + p.get("aud"));
        }
        long ahoraS = ahora.getEpochSecond();
        Long exp = numero(p, "exp");
        if (exp == null || exp + TOLERANCIA_S < ahoraS) {
            throw invalido("id_token vencido");
        }
        Long nbf = numero(p, "nbf");
        if (nbf != null && nbf - TOLERANCIA_S > ahoraS) {
            throw invalido("id_token aun no valido");
        }
        if (nonceEsperado == null || !nonceEsperado.equals(p.getString("nonce", null))) {
            throw invalido("el nonce del id_token no coincide");
        }
        if (p.getString("sub", "").isBlank()) {
            throw invalido("el id_token no trae sub");
        }
        return p;
    }

    private static void verificarRs256(Jwt jwt, Function<Boolean, JsonArray> jwks, int bitsMinimos) throws ErrorLoginMovil {
        String kid = jwt.header().getString("kid", null);
        JsonObject jwk = buscar(jwks.apply(false), kid);
        if (jwk == null) {
            jwk = buscar(jwks.apply(true), kid);
        }
        if (jwk == null) {
            throw invalido("no hay clave RSA en el JWKS para kid " + kid);
        }
        try {
            RSAPublicKey clave = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, Jwt.base64(jwk.getString("n"))), new BigInteger(1, Jwt.base64(jwk.getString("e")))));
            if (clave.getModulus().bitLength() < bitsMinimos) {
                throw invalido("clave RSA de " + clave.getModulus().bitLength() + " bits, se requieren " + bitsMinimos);
            }
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(clave);
            s.update(jwt.contenidoFirmado());
            if (!s.verify(jwt.firma())) {
                throw invalido("firma RS256 invalida");
            }
        } catch (GeneralSecurityException | RuntimeException e) {
            throw invalido("no se pudo verificar la firma: " + e.getMessage());
        }
    }

    private static JsonObject buscar(JsonArray claves, String kid) {
        if (claves == null) {
            return null;
        }
        for (JsonValue v : claves) {
            if (v instanceof JsonObject k && "RSA".equals(k.getString("kty", null))
                    && (kid == null || kid.equals(k.getString("kid", null)))
                    && (!k.containsKey("use") || "sig".equals(k.getString("use", null)))) {
                return k;
            }
        }
        return null;
    }

    private static boolean audienciaIncluye(JsonValue aud, String clientId) {
        if (aud instanceof JsonString s) {
            return s.getString().equals(clientId);
        }
        if (aud instanceof JsonArray a) {
            return a.stream().anyMatch(v -> v instanceof JsonString s && s.getString().equals(clientId));
        }
        return false;
    }

    private static Long numero(JsonObject p, String clave) {
        return p.get(clave) instanceof JsonNumber n ? n.longValue() : null;
    }

    private static byte[] hmac(String secreto, byte[] datos) throws ErrorLoginMovil {
        if (secreto == null || secreto.isEmpty()) {
            throw invalido("id_token HS256 sin client secret configurado");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(datos);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ErrorLoginMovil invalido(String detalle) {
        return new ErrorLoginMovil(Motivo.TOKEN_INVALIDO, "id_token invalido: " + detalle);
    }
}
