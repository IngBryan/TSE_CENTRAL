package tse.lab.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Configuración administrativa del descargo. No forma parte de las reglas del motor. */
@Entity
@Table(name = "configuracion_descargos")
public class ConfiguracionDescargos {
    @Id private Long id;
    @Column(nullable = false) private long revision;
    @Column(name = "plazo_dias", nullable = false) private int plazoDias;
    @Column(name = "actualizada_en", nullable = false) private Instant actualizadaEn;
    @Column(nullable = false, length = 64) private String usuario;
    @Column(nullable = false, length = 500) private String fundamento;

    protected ConfiguracionDescargos() { }
    public static ConfiguracionDescargos inicial() {
        ConfiguracionDescargos c = new ConfiguracionDescargos();
        c.id = 1L; c.revision = 1; c.plazoDias = 10; c.actualizadaEn = Instant.now();
        c.usuario = "SISTEMA"; c.fundamento = "Configuración inicial";
        return c;
    }
    public void actualizar(int dias, String usuario, String fundamento) {
        if (dias < 1 || dias > 365)
            throw new IllegalArgumentException("El plazo debe ser un entero entre 1 y 365 días");
        if (fundamento == null || fundamento.isBlank() || fundamento.strip().length() > 500)
            throw new IllegalArgumentException("El fundamento es obligatorio y admite hasta 500 caracteres");
        this.plazoDias = dias; this.usuario = usuario; this.fundamento = fundamento.strip();
        this.actualizadaEn = Instant.now(); this.revision++;
    }
    public long getRevision() { return revision; }
    public int getPlazoDias() { return plazoDias; }
    public Instant getActualizadaEn() { return actualizadaEn; }
    public String getUsuario() { return usuario; }
    public String getFundamento() { return fundamento; }
}
