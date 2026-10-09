package tse.lab.frontofficeciudadanos.model;

import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "responsables")
public class Responsable implements Ciudadano, Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String ci;
    private String email;
    @OneToOne
    @JoinColumn(name = "nro_empresa")
    private Empresa empresa;

    public Responsable() {
    }

    public Responsable(String ci, String email, Empresa empresa) {
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

    public void setEmail(String email) {
        this.email = email;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }
}
