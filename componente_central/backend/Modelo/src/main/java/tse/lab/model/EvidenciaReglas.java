package tse.lab.model;

import jakarta.persistence.*;
import java.time.Instant;

@Embeddable
public class EvidenciaReglas {
    @Column(nullable=false) private String matricula;
    @Column(name="fecha_hora", nullable=false) private Instant fechaHora;
    @Column(nullable=false) private String origen;
    @Column(nullable=false) private long revision;
    @Column(name="peso_base_kg") private Long pesoBaseKg;
    protected EvidenciaReglas() { }
    public EvidenciaReglas(String matricula, Instant fechaHora, String origen, long revision, Long pesoBaseKg) {
        this.matricula=matricula; this.fechaHora=fechaHora; this.origen=origen;
        this.revision=revision; this.pesoBaseKg=pesoBaseKg;
    }
    public boolean corresponde(String m, Instant f, String o) {
        return matricula.equals(m) && fechaHora.equals(f) && origen.equals(o);
    }
    public long getRevision() { return revision; }
    public Long getPesoBaseKg() { return pesoBaseKg; }
}
