package tse.lab.backofficeautoridadesadministradores.rest.seguridad;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import tse.lab.backofficeautoridadesadministradores.service.autenticacion.IdentidadBackoffice;

/**
 * Token of a backoffice user: JWT HS256 signed with BACKOFFICE_JWT_SECRET (only WildFly knows it), with the CI
 * (sub) and the role, valid for 8 hours. It replaces the HTTP session: with no state in the server, any WildFly
 * replica validates it. The role travels in the token, so a revoked role lasts until the token expires.
 */
public final class BackofficeToken {

    public static final long LIFETIME_S = 8 * 60 * 60;
    static final String ISSUER = "carga.uy";
    static final String AUDIENCE = "carga.uy-backoffice";

    private BackofficeToken() {
    }

    public static String issue(IdentidadBackoffice identity, byte[] secret, Instant now) {
        String header = base64(Json.createObjectBuilder().add("alg", "HS256").add("typ", "JWT").build());
        String payload = base64(Json.createObjectBuilder()
                .add("sub", identity.ci())
                .add("rol", identity.rol())
                .add("iss", ISSUER)
                .add("aud", AUDIENCE)
                .add("iat", now.getEpochSecond())
                .add("exp", now.getEpochSecond() + LIFETIME_S)
                .build());
        String signed = header + "." + payload;
        return signed + "." + base64(hmac(secret, signed.getBytes(StandardCharsets.US_ASCII)));
    }

    /** The identity in the token, if its signature, issuer, audience and expiration are valid. */
    public static Optional<IdentidadBackoffice> verify(String token, byte[] secret, Instant now) {
        if (token == null || secret.length == 0) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            JsonObject header = json(parts[0]);
            byte[] signed = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
            if (!"HS256".equals(header.getString("alg", null))
                    || !MessageDigest.isEqual(hmac(secret, signed), Base64.getUrlDecoder().decode(parts[2]))) {
                return Optional.empty();
            }
            JsonObject payload = json(parts[1]);
            String ci = payload.getString("sub", "");
            String role = payload.getString("rol", "");
            boolean valid = ISSUER.equals(payload.getString("iss", null))
                    && AUDIENCE.equals(payload.getString("aud", null))
                    && payload.get("exp") instanceof JsonNumber exp && exp.longValue() > now.getEpochSecond()
                    && !ci.isBlank() && !role.isBlank();
            return valid ? Optional.of(new IdentidadBackoffice(ci, role)) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty(); // not base64url or not JSON
        }
    }

    /** BACKOFFICE_JWT_SECRET: random string of at least 32 characters. Empty if it is not configured. */
    public static byte[] configuredSecret() {
        String value = System.getenv("BACKOFFICE_JWT_SECRET");
        return value == null || value.length() < 32 ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject json(String part) {
        try (JsonReader reader = Json.createReader(new StringReader(
                new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8)))) {
            return reader.readObject();
        }
    }

    private static String base64(JsonObject json) {
        return base64(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hmac(byte[] secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
