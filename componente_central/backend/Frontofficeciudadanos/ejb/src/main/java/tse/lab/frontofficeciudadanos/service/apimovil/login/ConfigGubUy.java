package tse.lab.frontofficeciudadanos.service.apimovil.login;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

// Cliente de ID Uruguay (gub.uy) que usa la app movil. Todo sale de variables de entorno: el client
// secret nunca va en el codigo ni en el APK. Los valores por defecto son los del ambiente de testing.
public record ConfigGubUy(
        String issuer,
        List<String> issuersAceptados,
        String tokenEndpoint,
        String userinfoEndpoint,
        String jwksUri,
        String clientId,
        String clientSecret,
        List<String> redirectUrisMovil,
        Duration timeout) {

    public static ConfigGubUy desdeEntorno() {
        String issuer = env("OIDC_ISSUER", "https://auth-testing.iduruguay.gub.uy/oidc/v1");
        return new ConfigGubUy(
                issuer,
                // Algunos tokens de testing traen el issuer sin "/v1": se aceptan los dos.
                lista(env("OIDC_ISSUERS_ACEPTADOS", issuer + "," + issuer.replaceAll("/v\\d+$", ""))),
                env("OIDC_TOKEN_ENDPOINT", issuer + "/token"),
                env("OIDC_USERINFO_ENDPOINT", issuer + "/userinfo"),
                env("OIDC_JWKS_URI", issuer + "/jwks"),
                env("OIDC_CLIENT_ID", ""),
                env("OIDC_CLIENT_SECRET", ""),
                // Redirect URIs registradas en ID Uruguay para la app (esquema propio o loopback)
                lista(env("OIDC_REDIRECT_URIS_MOVIL", "uy.carga.movil:/oauth2redirect,http://localhost:8080")),
                Duration.ofMillis(Long.parseLong(env("OIDC_TIMEOUT_MS", "8000"))));
    }

    public boolean completa() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    // Minimo de bits de la clave RSA del id_token: el ambiente de testing de ID Uruguay firma con 1024;
    // con cualquier otro issuer se exigen 2048.
    public int bitsMinimosRsa() {
        return "auth-testing.iduruguay.gub.uy".equals(URI.create(issuer).getHost()) ? 1024 : 2048;
    }

    private static String env(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }

    private static List<String> lista(String valor) {
        return Arrays.stream(valor.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
