package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

// Descargo que presenta la empresa ante un caso de fiscalizacion notificado (uno por caso). Lo presenta el
// responsable desde el frontoffice de ciudadanos y lo revisa el funcionario antes de resolver.
@Entity
@Table(name = "descargos")
public class Descargo implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String PRESENTADO = "PRESENTADO";
    public static final int MAX_DESCRIPCION = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = MAX_DESCRIPCION)
    private String descripcion;

    private String tipoCaso;

    private String estado;

    // Cuando y quien (cedula del responsable de la empresa) lo presento
    private Instant fechaPresentacion;

    private String ciPresentante;

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

    // Descargo recien presentado por el responsable de la empresa
    public static Descargo presentar(String descripcion, CasoFiscalizacion caso, String ciPresentante) {
        Descargo d = new Descargo(descripcion, caso.getTipoCaso(), PRESENTADO, caso);
        d.fechaPresentacion = Instant.now();
        d.ciPresentante = ciPresentante;
        return d;
    }

    public Instant getFechaPresentacion() {
        return fechaPresentacion;
    }

    public String getCiPresentante() {
        return ciPresentante;
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

