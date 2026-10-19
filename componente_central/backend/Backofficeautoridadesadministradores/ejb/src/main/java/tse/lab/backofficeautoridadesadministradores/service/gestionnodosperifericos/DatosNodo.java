
package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.io.Serializable;
import java.time.Instant;

// id: identificador interno del central.
// idExterno: identificador del nodo en su sistema de origen.
// Es una clase y no un record: el cliente de EJB remoto
// utiliza JBoss Marshalling para la deserializacion.
public final class DatosNodo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final Long idExterno;
    private final String nombre;
    private final TipoNodo tipo;
    private final String ubicacion;
    private final boolean activo;
    private final Instant ultimaComunicacionExitosa;

    public DatosNodo(
            Long id,
            Long idExterno,
            String nombre,
            TipoNodo tipo,
            String ubicacion,
            boolean activo,
            Instant ultimaComunicacionExitosa) {

        this.id = id;
        this.idExterno = idExterno;
        this.nombre = nombre;
        this.tipo = tipo;
        this.ubicacion = ubicacion;
        this.activo = activo;
        this.ultimaComunicacionExitosa = ultimaComunicacionExitosa;
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

    public String getUbicacion() {
        return ubicacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getUltimaComunicacionExitosa() {
        return ultimaComunicacionExitosa;
    }

    @Override
    public String toString() {
        return "DatosNodo["
                + "id=" + id
                + ", idExterno=" + idExterno
                + ", nombre=" + nombre
                + ", tipo=" + tipo
                + ", ubicacion=" + ubicacion
                + ", activo=" + activo
                + ", ultimaComunicacionExitosa="
                + ultimaComunicacionExitosa
                + "]";
    }
}
