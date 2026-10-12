package tse.lab.frontofficefuncionarios.model;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Responsable de una empresa. La empresa (tabla empresas) es de Frontofficeciudadanos: aca solo se
// guarda su numero, sin FK entre los dos persistence units.
@Entity
@Table(name = "responsables")
public class Responsable implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String ci;
    private String email;
    @Column(name = "nro_empresa")
    private Long nroEmpresa;

    public Responsable() {
    }

    public Responsable(String ci, String email, Long nroEmpresa) {
        this.ci = ci;
        this.email = email;
        this.nroEmpresa = nroEmpresa;
    }

    public String getCi() {
        return ci;
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

    public Long getNroEmpresa() {
        return nroEmpresa;
    }

    public void setNroEmpresa(Long nroEmpresa) {
        this.nroEmpresa = nroEmpresa;
    }
}
