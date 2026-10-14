package tse.lab.fiscalizacionautomatica.ruleengine.remote;

import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/** JNDI context to look up remote EJBs deployed in WildFly. */
public final class WildFlyContexts {

    private WildFlyContexts() {
    }

    /** @param url WildFly HTTP port, e.g. remote+http://wildfly:8080 */
    public static Context create(String url, String user, String password) throws NamingException {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
        props.put(Context.PROVIDER_URL, url);
        props.put(Context.SECURITY_PRINCIPAL, user);
        props.put(Context.SECURITY_CREDENTIALS, password);
        return new InitialContext(props);
    }

    public static void closeQuietly(Context context) {
        try {
            context.close();
        } catch (NamingException e) {
            // shutting down: nothing else to do
        }
    }
}
