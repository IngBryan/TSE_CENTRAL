package tse.lab.frontofficeciudadanos.service.apimovil.login;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import tse.lab.frontofficeciudadanos.service.apimovil.login.ErrorLoginMovil.Motivo;

// Llamadas HTTP a ID Uruguay: Token Endpoint (canje del code), JWKS y UserInfo. Con timeout: si gub.uy
// no responde el login falla con idp_no_disponible y el resto del central sigue andando.
public class ClienteGubUy {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final long JWKS_VIGENCIA_MS = 10 * 60_000;

    // Cache del JWKS compartida por todas las instancias (solo claves publicas)
    private static volatile JsonArray jwksCache;
    private static volatile long jwksVence;

    private final ConfigGubUy cfg;

    public ClienteGubUy(ConfigGubUy cfg) {
        this.cfg = cfg;
    }

    public record Tokens(String idToken, String accessToken) {
    }

    // Token Request (authorization_code): client_id/client_secret en HTTP Basic, con la misma
    // redirect_uri y el code_verifier (PKCE) que uso la app en el authorize.
    public Tokens canjear(String code, String redirectUri, String codeVerifier) throws ErrorLoginMovil {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", codeVerifier);
        String basic = Base64.getEncoder().encodeToString(
                (codificar(cfg.clientId()) + ":" + codificar(cfg.clientSecret())).getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> res = enviar(HttpRequest.newBuilder(URI.create(cfg.tokenEndpoint()))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.entrySet().stream()
                        .map(e -> codificar(e.getKey()) + "=" + codificar(e.getValue()))
                        .collect(Collectors.joining("&")))));
        JsonObject cuerpo = jsonONull(res.body());
        if (res.statusCode() != 200 || cuerpo == null
                || cuerpo.getString("id_token", null) == null || cuerpo.getString("access_token", null) == null) {
            throw new ErrorLoginMovil(Motivo.TOKEN_INVALIDO, "token endpoint respondio " + res.statusCode() + ": "
                    + (cuerpo == null ? "" : cuerpo.getString("error", "") + " " + cuerpo.getString("error_description", "")));
        }
        return new Tokens(cuerpo.getString("id_token"), cuerpo.getString("access_token"));
    }

    // Para VerificadorIdToken: forzar=true vuelve a pedir el JWKS aunque la cache este vigente.
    public JsonArray jwks(boolean forzar) {
        if (!forzar && jwksCache != null && jwksVence > System.currentTimeMillis()) {
            return jwksCache;
        }
        try {
            HttpResponse<String> res = enviar(HttpRequest.newBuilder(URI.create(cfg.jwksUri()))
                    .header("Accept", "application/json").GET());
            JsonObject cuerpo = jsonONull(res.body());
            if (res.statusCode() != 200 || cuerpo == null || !(cuerpo.get("keys") instanceof JsonArray claves)) {
                return jwksCache;
            }
            jwksCache = claves;
            jwksVence = System.currentTimeMillis() + JWKS_VIGENCIA_MS;
            return claves;
        } catch (ErrorLoginMovil e) {
            return jwksCache;
        }
    }

    // UserInfo: cedula (numero_documento o uid), nombre y email. El sub tiene que ser el del id_token.
    public JsonObject userinfo(String accessToken, String subEsperado) throws ErrorLoginMovil {
        HttpResponse<String> res = enviar(HttpRequest.newBuilder(URI.create(cfg.userinfoEndpoint()))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET());
        JsonObject claims = jsonONull(res.body());
        if (res.statusCode() != 200 || claims == null) {
            throw new ErrorLoginMovil(Motivo.TOKEN_INVALIDO, "userinfo respondio " + res.statusCode());
        }
        if (!subEsperado.equals(claims.getString("sub", null))) {
            throw new ErrorLoginMovil(Motivo.TOKEN_INVALIDO, "el sub de userinfo no coincide con el del id_token");
        }
        return claims;
    }

    private HttpResponse<String> enviar(HttpRequest.Builder req) throws ErrorLoginMovil {
        String destino = "";
        try {
            HttpRequest r = req.timeout(cfg.timeout()).build();
            destino = r.uri().toString();
            HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 500) {
                throw new ErrorLoginMovil(Motivo.IDP_NO_DISPONIBLE, destino + " respondio " + res.statusCode());
            }
            return res;
        } catch (IOException e) {
            throw new ErrorLoginMovil(Motivo.IDP_NO_DISPONIBLE, "no se pudo contactar " + destino + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorLoginMovil(Motivo.IDP_NO_DISPONIBLE, "interrumpido llamando a " + destino, e);
        }
    }

    private static JsonObject jsonONull(String texto) {
        try {
            return Jwt.json(texto);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String codificar(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
