package tse.lab.model;

import jakarta.persistence.Column;
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

    // Un funcionario al que se le quita el rol no se borra: sus fundamentos de resolucion lo referencian y son
    // parte de la auditoria. Queda inactivo: no puede ingresar ni operar. El default es para los INSERT que no
    // lo mencionan (datos-prueba.sql)
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo = true;

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

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public List<FundamentoResolucion> getFundamentosResolucion() {
        return fundamentosResolucion;
    }
}

