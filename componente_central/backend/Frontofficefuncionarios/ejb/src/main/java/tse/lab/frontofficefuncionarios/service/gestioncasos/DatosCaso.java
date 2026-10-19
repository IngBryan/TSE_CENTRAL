package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.io.Serializable;
import java.util.Objects;

// Un caso de fiscalizacion visto desde la fiscalizacion automatica. estado: nombre de EstadoCaso.
// Es una clase y no un record: el cliente de EJB remoto (JBoss Marshalling) no puede deserializar records.
public final class DatosCaso implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String tipoCaso;
    private final String estado;

    public DatosCaso(long id, String tipoCaso, String estado) {
        this.id = id;
        this.tipoCaso = tipoCaso;
        this.estado = estado;
    }

    public long getId() {
        return id;
    }

    public String getTipoCaso() {
        return tipoCaso;
    }

    public String getEstado() {
        return estado;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DatosCaso otro && id == otro.id && Objects.equals(tipoCaso, otro.tipoCaso)
                && Objects.equals(estado, otro.estado);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tipoCaso, estado);
    }

    @Override
    public String toString() {
        return "DatosCaso[id=" + id + ", tipoCaso=" + tipoCaso + ", estado=" + estado + "]";
    }
}
