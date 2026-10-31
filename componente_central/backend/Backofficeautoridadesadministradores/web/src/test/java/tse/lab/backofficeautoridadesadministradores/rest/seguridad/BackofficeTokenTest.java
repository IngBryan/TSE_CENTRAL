package tse.lab.backofficeautoridadesadministradores.rest.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tse.lab.backofficeautoridadesadministradores.service.autenticacion.IdentidadBackoffice;

class BackofficeTokenTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final IdentidadBackoffice ADMIN = new IdentidadBackoffice("99999999", "ADMINISTRADOR");

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** A token with any header and payload, correctly signed with the secret. */
    private static String signed(String header, String payload) throws Exception {
        String content = base64(header) + "." + base64(payload);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        return content + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void aTokenIsValidWithItsSecretUntilItExpires() {
        String token = BackofficeToken.issue(ADMIN, SECRET, NOW);

        assertEquals(ADMIN, BackofficeToken.verify(token, SECRET, NOW).orElseThrow());
        assertEquals(ADMIN,
                BackofficeToken.verify(token, SECRET, NOW.plusSeconds(BackofficeToken.LIFETIME_S - 1)).orElseThrow());
        assertTrue(BackofficeToken.verify(token, SECRET, NOW.plusSeconds(BackofficeToken.LIFETIME_S)).isEmpty());
    }

    @Test
    void anotherSecretDoesNotValidateIt() {
        String token = BackofficeToken.issue(ADMIN, SECRET, NOW);

        assertTrue(BackofficeToken.verify(token, "another-secret-of-at-least-32-chars!".getBytes(StandardCharsets.UTF_8),
                NOW).isEmpty());
        assertTrue(BackofficeToken.verify(token, new byte[0], NOW).isEmpty());
    }

    @Test
    void changingThePayloadBreaksTheSignature() {
        String[] parts = BackofficeToken.issue(new IdentidadBackoffice("88888888", "AUTORIDAD"), SECRET, NOW)
                .split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("AUTORIDAD", "ADMINISTRADOR");

        assertTrue(BackofficeToken.verify(parts[0] + "." + base64(payload) + "." + parts[2], SECRET, NOW).isEmpty());
    }

    @Test
    void onlyHs256IsAccepted() throws Exception {
        String payload = "{\"sub\":\"99999999\",\"rol\":\"ADMINISTRADOR\",\"iss\":\"carga.uy\","
                + "\"aud\":\"carga.uy-backoffice\",\"exp\":" + (NOW.getEpochSecond() + 60) + "}";

        assertEquals(ADMIN, BackofficeToken.verify(signed("{\"alg\":\"HS256\"}", payload), SECRET, NOW).orElseThrow());
        assertTrue(BackofficeToken.verify(signed("{\"alg\":\"none\"}", payload), SECRET, NOW).isEmpty());
    }

    @Test
    void aTokenForAnotherAudienceIsRejected() throws Exception {
        // Same signing scheme as the mobile app token, which must not open the backoffice
        String payload = "{\"sub\":\"99999999\",\"rol\":\"ADMINISTRADOR\",\"iss\":\"carga.uy\","
                + "\"aud\":\"carga.uy-movil\",\"exp\":" + (NOW.getEpochSecond() + 60) + "}";

        assertTrue(BackofficeToken.verify(signed("{\"alg\":\"HS256\"}", payload), SECRET, NOW).isEmpty());
    }

    @Test
    void aTokenWithoutExpirationOrRoleIsRejected() throws Exception {
        String header = "{\"alg\":\"HS256\"}";
        String withoutExp = "{\"sub\":\"99999999\",\"rol\":\"ADMINISTRADOR\",\"iss\":\"carga.uy\","
                + "\"aud\":\"carga.uy-backoffice\"}";
        String withoutRole = "{\"sub\":\"99999999\",\"iss\":\"carga.uy\",\"aud\":\"carga.uy-backoffice\",\"exp\":"
                + (NOW.getEpochSecond() + 60) + "}";

        assertTrue(BackofficeToken.verify(signed(header, withoutExp), SECRET, NOW).isEmpty());
        assertTrue(BackofficeToken.verify(signed(header, withoutRole), SECRET, NOW).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-token", "a.b", "a.b.c", "!!!.???.***", "e30.e30.e30.e30"})
    void malformedTokensAreRejected(String token) {
        assertTrue(BackofficeToken.verify(token, SECRET, NOW).isEmpty());
    }
}
