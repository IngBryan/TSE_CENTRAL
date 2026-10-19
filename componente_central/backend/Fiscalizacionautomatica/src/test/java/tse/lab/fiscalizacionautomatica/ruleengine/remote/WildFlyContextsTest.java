package tse.lab.fiscalizacionautomatica.ruleengine.remote;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import javax.naming.Context;
import javax.naming.NamingException;
import org.junit.jupiter.api.Test;

class WildFlyContextsTest {

    @Test
    void theContextPointsToWildFlyWithTheCredentials() throws NamingException {
        // creating it does not connect: the connection opens with the first remote call
        Context context = WildFlyContexts.create("remote+http://localhost:8080", "fiscalizacion", "secret");

        assertEquals("remote+http://localhost:8080", context.getEnvironment().get(Context.PROVIDER_URL));
        assertEquals("fiscalizacion", context.getEnvironment().get(Context.SECURITY_PRINCIPAL));
        WildFlyContexts.closeQuietly(context);
    }

    @Test
    void closingQuietlyIgnoresAFailure() throws NamingException {
        Context context = mock(Context.class);
        doThrow(new NamingException("already closed")).when(context).close();

        assertDoesNotThrow(() -> WildFlyContexts.closeQuietly(context));
    }
}
