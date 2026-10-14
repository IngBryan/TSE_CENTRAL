package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "fundamentos_resolucion")
public class FundamentoResolucion implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = RegistroAuditoriaCaso.MAX_FUNDAMENTO)
    private String descripcion;

    private Instant fechaHora;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ci_funcionario", nullable = false)
    private Funcionario funcionario;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_caso", nullable = false, unique = true)
    private CasoFiscalizacion casoFiscalizacion;

    public FundamentoResolucion() {
    }

    public FundamentoResolucion(String descripcion, Funcionario funcionario, CasoFiscalizacion casoFiscalizacion) {
        this.descripcion = descripcion;
        this.funcionario = funcionario;
        this.casoFiscalizacion = casoFiscalizacion;
        this.fechaHora = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Funcionario getFuncionario() {
        return funcionario;
    }

    public void setFuncionario(Funcionario funcionario) {
        this.funcionario = funcionario;
    }

    public CasoFiscalizacion getCasoFiscalizacion() {
        return casoFiscalizacion;
    }

    public void setCasoFiscalizacion(CasoFiscalizacion casoFiscalizacion) {
        this.casoFiscalizacion = casoFiscalizacion;
    }
}
