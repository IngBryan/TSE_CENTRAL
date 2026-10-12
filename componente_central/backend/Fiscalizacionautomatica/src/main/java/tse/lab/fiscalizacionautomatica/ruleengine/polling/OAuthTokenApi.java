package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/** OAuth 2.0 token endpoint of the balanzas node, implemented by MicroProfile Rest Client. */
@Path("/api/oauth/token")
public interface OAuthTokenApi {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TokenResponse requestToken(@HeaderParam(HttpHeaders.AUTHORIZATION) String basicCredentials,
            @FormParam("grant_type") String grantType);
}
