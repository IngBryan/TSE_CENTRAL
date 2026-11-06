package tse.lab.frontofficeciudadanos.data;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import jakarta.transaction.TransactionSynchronizationRegistry;

// The tests run without JTA: nothing to wait for, so the audit trail line is not written.
public final class NoOpTransactions {

    private NoOpTransactions() {
    }

    public static CasoFiscalizacionDAO wired(CasoFiscalizacionDAO dao) throws ReflectiveOperationException {
        Field field = CasoFiscalizacionDAO.class.getDeclaredField("transacciones");
        field.setAccessible(true);
        field.set(dao, Proxy.newProxyInstance(NoOpTransactions.class.getClassLoader(),
                new Class<?>[] { TransactionSynchronizationRegistry.class }, (proxy, method, args) -> null));
        return dao;
    }
}
