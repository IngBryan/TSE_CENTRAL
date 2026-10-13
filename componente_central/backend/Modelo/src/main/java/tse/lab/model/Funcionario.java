package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "funcionarios")
public class Funcionario implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String ci;

    private String email;

    @OneToMany(mappedBy = "funcionario")
    private List<FundamentoResolucion> fundamentosResolucion = new ArrayList<>();

    public Funcionario() {
    }

    public Funcionario(String ci, String email) {
        this.ci = ci;
        this.email = email;
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

    public List<FundamentoResolucion> getFundamentosResolucion() {
        return fundamentosResolucion;
    }
}

