package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.io.Serializable;
import java.util.Objects;

// id: el del central. idExterno: el del nodo en su sistema (balanza en la API de balanzas, gpsId en tracking).
// Es una clase y no un record: el cliente de EJB remoto (JBoss Marshalling) no puede deserializar records.
public final class DatosNodo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final Long idExterno;
    private final String nombre;
    private final TipoNodo tipo;

    public DatosNodo(Long id, Long idExterno, String nombre, TipoNodo tipo) {
        this.id = id;
        this.idExterno = idExterno;
        this.nombre = nombre;
        this.tipo = tipo;
    }

    public Long getId() {
        return id;
    }

    public Long getIdExterno() {
        return idExterno;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoNodo getTipo() {
        return tipo;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DatosNodo otro && Objects.equals(id, otro.id) && Objects.equals(idExterno, otro.idExterno)
                && Objects.equals(nombre, otro.nombre) && tipo == otro.tipo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, idExterno, nombre, tipo);
    }

    @Override
    public String toString() {
        return "DatosNodo[id=" + id + ", idExterno=" + idExterno + ", nombre=" + nombre + ", tipo=" + tipo + "]";
    }
}
