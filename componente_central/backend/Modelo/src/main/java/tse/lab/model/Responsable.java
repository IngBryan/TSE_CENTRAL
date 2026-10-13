package tse.lab.model;

import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

// Rol de un ciudadano como responsable de una empresa: solo ci y nro_empresa. Los datos de la
// persona (nombre, email, validacion) estan en Ciudadano. Un responsable es de una unica empresa
// (la ci es la clave) y una empresa tiene un unico responsable (nro_empresa es unico).
@Entity
@Table(name = "responsables")
public class Responsable implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String ci;
    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "ci")
    private Ciudadano ciudadano;
    @OneToOne(optional = false)
    @JoinColumn(name = "nro_empresa", nullable = false, unique = true)
    private Empresa empresa;

    public Responsable() {
    }

    public Responsable(Ciudadano ciudadano, Empresa empresa) {
        this.ci = ciudadano.getCi();
        this.ciudadano = ciudadano;
        this.empresa = empresa;
    }

    public String getCi() {
        return ci;
    }

    public Ciudadano getCiudadano() {
        return ciudadano;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }
}
