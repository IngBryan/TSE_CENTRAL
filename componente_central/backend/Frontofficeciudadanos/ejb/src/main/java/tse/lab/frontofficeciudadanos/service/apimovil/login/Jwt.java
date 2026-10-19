package tse.lab.frontofficeciudadanos.service.apimovil.login;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

// Partes de un JWT compacto (header.payload.firma). No valida nada: eso lo hacen VerificadorIdToken
// (id_token de gub.uy) y TokenMovil (token de la app).
record Jwt(JsonObject header, JsonObject payload, byte[] contenidoFirmado, byte[] firma) {

    static Jwt leer(String token) {
        String[] partes = token == null ? new String[0] : token.split("\\.", -1);
        if (partes.length != 3) {
            throw new IllegalArgumentException("no es un JWT compacto");
        }
        return new Jwt(
                json(new String(base64(partes[0]), StandardCharsets.UTF_8)),
                json(new String(base64(partes[1]), StandardCharsets.UTF_8)),
                (partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII),
                base64(partes[2]));
    }

    static JsonObject json(String texto) {
        try (JsonReader r = Json.createReader(new StringReader(texto))) {
            return r.readObject();
        }
    }

    static byte[] base64(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    static String base64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
