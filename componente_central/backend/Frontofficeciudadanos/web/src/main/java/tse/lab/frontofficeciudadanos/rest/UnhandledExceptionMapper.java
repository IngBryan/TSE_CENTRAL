package tse.lab.frontofficeciudadanos.rest;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import tse.lab.frontofficeciudadanos.service.LogCiudadanos;

// What no resource handled becomes a 500 here: without a mapper the response filter does not run, and the request
// would end with no access log and no correlation id.
@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class.getName());

    @Context
    private HttpServletRequest req;

    @Override
    public Response toResponse(Throwable e) {
        if (e instanceof WebApplicationException conRespuesta) {
            return conRespuesta.getResponse();
        }
        LogCiudadanos.evento("http.error").nivel(Level.SEVERE)
                .correlationId((String) req.getAttribute(ApiKeyFilter.CORRELATION_ID)).error(e).escribirEn(LOG);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "error_interno")).build();
    }
}
