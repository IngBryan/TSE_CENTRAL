package tse.lab.frontofficeciudadanos.service.apimovil.login;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;

// Token de la app movil: JWT HS256 firmado con MOVIL_TOKEN_SECRET (solo lo conoce el central). Lleva
// la cedula (sub) y el nombre y vence a las 8 horas: despues el chofer vuelve a ingresar con gub.uy.
// No lleva roles: ViajesChoferService controla en cada llamada que siga siendo chofer habilitado.
// Sin estado en el servidor: cualquier instancia de WildFly lo valida.
public final class TokenMovil {

    public static final long DURACION_S = 8 * 60 * 60;
    static final String EMISOR = "carga.uy";
    static final String AUDIENCIA = "carga.uy-movil";

    public record Emitido(String token, long expira) {
    }

    public static class TokenInvalidoException extends Exception {
        private final boolean vencido;

        TokenInvalidoException(String detalle, boolean vencido) {
            super(detalle);
            this.vencido = vencido;
        }

        public boolean isVencido() {
            return vencido;
        }
    }

    private TokenMovil() {
    }

    public static Emitido emitir(String cedula, String nombre, byte[] secreto, Instant ahora) {
        long expira = ahora.getEpochSecond() + DURACION_S;
        String header = Jwt.base64(Json.createObjectBuilder().add("alg", "HS256").add("typ", "JWT").build()
                .toString().getBytes(StandardCharsets.UTF_8));
        String payload = Jwt.base64(Json.createObjectBuilder()
                .add("sub", cedula)
                .add("nombre", nombre == null ? "" : nombre)
                .add("iss", EMISOR)
                .add("aud", AUDIENCIA)
                .add("iat", ahora.getEpochSecond())
                .add("exp", expira)
                .add("jti", UUID.randomUUID().toString())
                .build().toString().getBytes(StandardCharsets.UTF_8));
        String contenido = header + "." + payload;
        return new Emitido(contenido + "." + Jwt.base64(hmac(secreto, contenido.getBytes(StandardCharsets.US_ASCII))), expira);
    }

    // Devuelve la cedula del token si la firma, el emisor, la audiencia y el vencimiento son validos.
    public static String validar(String token, byte[] secreto, Instant ahora) throws TokenInvalidoException {
        Jwt jwt;
        try {
            jwt = Jwt.leer(token);
        } catch (RuntimeException e) {
            throw new TokenInvalidoException("token mal formado", false);
        }
        if (!"HS256".equals(jwt.header().getString("alg", null))
                || !MessageDigest.isEqual(hmac(secreto, jwt.contenidoFirmado()), jwt.firma())) {
            throw new TokenInvalidoException("firma invalida", false);
        }
        JsonObject p = jwt.payload();
        if (!EMISOR.equals(p.getString("iss", null)) || !AUDIENCIA.equals(p.getString("aud", null))) {
            throw new TokenInvalidoException("emisor o audiencia invalidos", false);
        }
        JsonNumber exp = p.get("exp") instanceof JsonNumber n ? n : null;
        if (exp == null || exp.longValue() <= ahora.getEpochSecond()) {
            throw new TokenInvalidoException("token vencido", true);
        }
        String sub = p.getString("sub", null);
        if (sub == null || sub.isBlank()) {
            throw new TokenInvalidoException("token sin sub", false);
        }
        return sub;
    }

    // MOVIL_TOKEN_SECRET: string aleatorio de al menos 32 caracteres. Vacio si no esta configurado.
    public static byte[] secretoConfigurado() {
        String valor = System.getenv("MOVIL_TOKEN_SECRET");
        return valor == null || valor.length() < 32 ? new byte[0] : valor.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] secreto, byte[] datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return mac.doFinal(datos);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
