package tse.lab.fiscalizacionautomatica.ruleengine.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import javax.naming.Context;
import javax.naming.NamingException;
import org.junit.jupiter.api.Test;
import tse.lab.backofficeautoridadesadministradores.service.reglas.ConfiguracionReglasRemote;
import tse.lab.fiscalizacionautomatica.ruleengine.remote.WildFlyContexts;

class ReglasClientTest {
    @Test void consultaSnapshotsYTraduceFallosRemotosSinPerderLaCausa() throws Exception {
        var context=mock(Context.class);var remote=mock(ConfiguracionReglasRemote.class);
        when(context.lookup(ConfiguracionReglasRemote.LOOKUP)).thenReturn(remote);
        var snapshot=EstadoReglasTest.c(2);
        when(remote.obtenerVigente()).thenReturn(snapshot);when(remote.obtenerRevision(2)).thenReturn(snapshot);
        try (var contexts=mockStatic(WildFlyContexts.class)) {
            contexts.when(()->WildFlyContexts.create("url","u","p")).thenReturn(context);
            try (var cliente=new ReglasClient("url","u","p")) {
                assertSame(snapshot,cliente.vigente());assertSame(snapshot,cliente.revision(2));
                var fallo=new IllegalStateException("EJB no disponible");
                when(remote.obtenerVigente()).thenThrow(fallo);when(remote.obtenerRevision(3)).thenThrow(fallo);
                assertSame(fallo,assertThrows(IOException.class,cliente::vigente).getCause());
                assertSame(fallo,assertThrows(IOException.class,()->cliente.revision(3)).getCause());
            }
            contexts.verify(()->WildFlyContexts.closeQuietly(context));
        }
    }
    @Test void unLookupFallidoCierraElContextoQueSeAlcanzoACrear() throws Exception {
        var context=mock(Context.class);when(context.lookup(anyString())).thenThrow(new NamingException("sin EJB"));
        try (var contexts=mockStatic(WildFlyContexts.class)) {
            contexts.when(()->WildFlyContexts.create("url","u","p")).thenReturn(context);
            assertThrows(IOException.class,()->new ReglasClient("url","u","p"));
            contexts.verify(()->WildFlyContexts.closeQuietly(context));
        }
    }
    @Test void unFalloAlCrearElContextoSePropagaComoIOException() {
        try (var contexts=mockStatic(WildFlyContexts.class)) {
            contexts.when(()->WildFlyContexts.create("url","u","p")).thenThrow(new NamingException("sin servidor"));
            assertThrows(IOException.class,()->new ReglasClient("url","u","p"));
            contexts.verify(()->WildFlyContexts.closeQuietly(any()),never());
        }
    }
}
