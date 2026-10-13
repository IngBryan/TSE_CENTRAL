package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "descargos")
public class Descargo implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descripcion;

    private String tipoCaso;

    private String estado;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_caso", nullable = false, unique = true)
    private CasoFiscalizacion casoFiscalizacion;

    public Descargo() {
    }

    public Descargo(String descripcion, String tipoCaso, String estado, CasoFiscalizacion casoFiscalizacion) {
        this.descripcion = descripcion;
        this.tipoCaso = tipoCaso;
        this.estado = estado;
        this.casoFiscalizacion = casoFiscalizacion;
    }

    public Long getId() {
        return id;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTipoCaso() {
        return tipoCaso;
    }

    public void setTipoCaso(String tipoCaso) {
        this.tipoCaso = tipoCaso;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public CasoFiscalizacion getCasoFiscalizacion() {
        return casoFiscalizacion;
    }

    public void setCasoFiscalizacion(CasoFiscalizacion casoFiscalizacion) {
        this.casoFiscalizacion = casoFiscalizacion;
    }
}

