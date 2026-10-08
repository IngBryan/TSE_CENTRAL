package tse.lab.frontofficeciudadanos.model;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "choferes")
public class Chofer implements Serializable, Ciudadano {
    private static final long serialVersionUID = 1L;
    @Id
    private String ci;
    private String email;
    @ManyToOne
    @JoinColumn(name = "nro_empresa")
    private Empresa empresa;

    public Chofer() {
    }

    public Chofer(String ci, String email, Empresa empresa) {
        this.ci = ci;
        this.email = email;
        this.empresa = empresa;
    }

    public String getCi() {
        return ci;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setCi(String ci) {
        this.ci = ci;
    }

    public String getEmail() {
        return email;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
