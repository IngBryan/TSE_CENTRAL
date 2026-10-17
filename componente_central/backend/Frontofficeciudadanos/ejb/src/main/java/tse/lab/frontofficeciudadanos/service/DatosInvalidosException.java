package tse.lab.frontofficeciudadanos.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.ejb.ApplicationException;

// Datos de entrada que no cumplen las reglas; errores = campo -> mensaje. La REST la traduce a 400.
@ApplicationException(rollback = true)
public class DatosInvalidosException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Map<String, String> errores;

    public DatosInvalidosException(Map<String, String> errores) {
        super("Datos invalidos: " + errores.keySet());
        this.errores = Collections.unmodifiableMap(new LinkedHashMap<>(errores));
    }

    public Map<String, String> getErrores() {
        return errores;
    }
}
