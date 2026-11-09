package tse.lab.model;

import jakarta.persistence.*;

@Entity
@Table(name="reglas_fiscalizacion_vigente")
public class ReglasVigentes {
    @Id private Long id;
    @Column(nullable=false) private long revision;
    @Version private long version;
    protected ReglasVigentes() { }
    public ReglasVigentes(long revision) { id=1L; this.revision=revision; }
    public long getRevision() { return revision; }
    public void avanzar(long revision) { this.revision=revision; }
}
