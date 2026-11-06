package tse.lab.frontofficeciudadanos.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

class UnhandledExceptionMapperTest {

    private final UnhandledExceptionMapper mapper = new UnhandledExceptionMapper();

    @BeforeEach
    void setUp() throws Exception {
        Field field = UnhandledExceptionMapper.class.getDeclaredField("req");
        field.setAccessible(true);
        field.set(mapper, Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> null));
    }

    @Test
    void anUnhandledExceptionIsA500WithoutItsDetail() {
        Response r = mapper.toResponse(new IllegalStateException("database password in the message"));

        assertEquals(500, r.getStatus());
        assertEquals(Map.of("error", "error_interno"), r.getEntity());
    }

    @Test
    void anExceptionWithItsOwnResponseKeepsIt() {
        Response propia = Response.status(404).build();

        assertSame(propia, mapper.toResponse(new NotFoundException(propia)));
    }
}
