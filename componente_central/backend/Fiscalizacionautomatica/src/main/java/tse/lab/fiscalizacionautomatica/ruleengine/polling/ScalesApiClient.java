package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.ScalesRestApi.WeighingResponse;

/** Client of the balanzas node, built with MicroProfile Rest Client and authenticated with OAuth 2.0. */
public class ScalesApiClient implements ScalesApi {

    private static final long TIMEOUT_SECONDS = 10;

    /** Dates go to the balanzas node in Uruguay time, e.g. 2026-10-12T17:41:00.000-03:00. */
    private static final DateTimeFormatter URUGUAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.of("America/Montevideo"));

    private final ScalesRestApi api;
    private final BearerTokenFilter bearerToken;

    public ScalesApiClient(String baseUrl, String clientId, String clientSecret, Clock clock) {
        URI baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.bearerToken = new BearerTokenFilter(restClient(baseUri).build(OAuthTokenApi.class), clientId,
                clientSecret, clock);
        this.api = restClient(baseUri).register(bearerToken).build(ScalesRestApi.class);
    }

    private static RestClientBuilder restClient(URI baseUri) {
        return RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public List<Weighing> listWeighings(long scaleId, Instant from, Instant to)
            throws IOException {
        return withTokenRetry(() -> api.listWeighings(scaleId, format(from), format(to))).stream()
                .flatMap(w -> toWeighing(w).stream())
                .toList();
    }

    /** If the API rejects the token (e.g. the node restarted with another key), retries once with a new one. */
    private <T> T withTokenRetry(Supplier<T> request) throws IOException {
        try {
            try {
                return request.get();
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() != 401) {
                    throw e;
                }
                bearerToken.invalidate();
                return request.get();
            }
        } catch (WebApplicationException e) {
            throw new IOException("Balanzas API returned HTTP " + e.getResponse().getStatus(), e);
        } catch (ProcessingException e) {
            throw new IOException("Could not call the balanzas API: " + e.getMessage(), e);
        }
    }

    /** Incomplete or malformed weighings are skipped, so they don't block the rest of the scale. */
    private static Optional<Weighing> toWeighing(WeighingResponse w) {
        if (w.id() == null || w.scaleId() == null || w.licensePlate() == null || w.datetime() == null
                || w.weight() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Weighing(w.id(), w.scaleId(), w.licensePlate(),
                    OffsetDateTime.parse(w.datetime()).toInstant(), w.weight()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String format(Instant instant) {
        return URUGUAY_DATE_TIME.format(instant);
    }
}
