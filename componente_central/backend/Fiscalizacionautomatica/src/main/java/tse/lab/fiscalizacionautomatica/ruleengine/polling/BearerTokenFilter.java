package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.net.URLEncoder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.OAuthTokenApi.TokenResponse;

/**
 * Adds an OAuth 2.0 access token (client credentials grant) to every request. The token is reused
 * until shortly before it expires.
 */
public class BearerTokenFilter implements ClientRequestFilter {

    /** The token is renewed a bit before it expires, so it doesn't expire mid-request. */
    private static final Duration RENEWAL_MARGIN = Duration.ofSeconds(30);

    private final OAuthTokenApi tokenApi;
    private final String basicCredentials;
    private final Clock clock;

    private String accessToken;
    private Instant expiresAt = Instant.MIN;

    public BearerTokenFilter(OAuthTokenApi tokenApi, String clientId, String clientSecret, Clock clock) {
        this.tokenApi = tokenApi;
        // RFC 6749 §2.3.1: client id and secret are form-encoded before going into the Basic header
        String credentials = URLEncoder.encode(clientId, UTF_8) + ":" + URLEncoder.encode(clientSecret, UTF_8);
        this.basicCredentials = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));
        this.clock = clock;
    }

    @Override
    public void filter(ClientRequestContext request) {
        request.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + token());
    }

    /** Discards the current token, so the next request gets a new one. */
    public synchronized void invalidate() {
        accessToken = null;
    }

    private synchronized String token() {
        if (accessToken != null && clock.instant().isBefore(expiresAt)) {
            return accessToken;
        }
        TokenResponse token = tokenApi.requestToken(basicCredentials, "client_credentials");
        if (token.accessToken() == null || token.expiresIn() == null) {
            throw new ProcessingException("Token response without access_token or expires_in");
        }
        accessToken = token.accessToken();
        expiresAt = clock.instant().plusSeconds(token.expiresIn()).minus(RENEWAL_MARGIN);
        return accessToken;
    }
}
