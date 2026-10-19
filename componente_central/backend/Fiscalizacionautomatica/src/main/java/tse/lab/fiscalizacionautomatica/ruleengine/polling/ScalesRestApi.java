package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** REST API of the balanzas node, implemented by MicroProfile Rest Client (see {@link ScalesApiClient}). */
@Path("/api")
public interface ScalesRestApi {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WeighingResponse(Long id, Long scaleId, String licensePlate, String datetime, Long weight) {
    }

    /** Weighings of every vehicle in a scale between {@code from} and {@code to} (ISO 8601, both inclusive). */
    @GET
    @Path("/scales/{id}/weighings")
    @Produces(MediaType.APPLICATION_JSON)
    List<WeighingResponse> listWeighings(@PathParam("id") long scaleId, @QueryParam("from") String from,
            @QueryParam("to") String to);
}
