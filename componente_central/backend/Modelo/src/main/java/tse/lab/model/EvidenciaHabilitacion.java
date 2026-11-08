package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/** Copia de los dos documentos considerados al evaluar un evento; no cambia al renovarlos. */
@Embeddable
public class EvidenciaHabilitacion implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");

    public enum EstadoDocumento { VIGENTE, VENCIDO, AUN_NO_VIGENTE, SIN_REGISTRO }

    @Column(nullable = false)
    private String matricula;
    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;
    @Column(nullable = false)
    private String origen;
    private String nroPermiso;
    private LocalDate emisionPermiso;
    private LocalDate vencimientoPermiso;
    private LocalDate emisionItv;
    private LocalDate vencimientoItv;

    protected EvidenciaHabilitacion() { }

    public EvidenciaHabilitacion(String matricula, Instant fechaHora, String nroPermiso,
            LocalDate emisionPermiso, LocalDate vencimientoPermiso,
            LocalDate emisionItv, LocalDate vencimientoItv) {
        this.matricula = Objects.requireNonNull(matricula);
        this.fechaHora = Objects.requireNonNull(fechaHora);
        if ((emisionPermiso == null) != (vencimientoPermiso == null)
                || (nroPermiso == null) != (emisionPermiso == null)
                || (emisionItv == null) != (vencimientoItv == null)) {
            throw new IllegalArgumentException("Documento incompleto");
        }
        this.nroPermiso = nroPermiso;
        this.emisionPermiso = emisionPermiso;
        this.vencimientoPermiso = vencimientoPermiso;
        this.emisionItv = emisionItv;
        this.vencimientoItv = vencimientoItv;
    }

    public static EvidenciaHabilitacion evaluar(String matricula, Instant fechaHora,
            PermisoCirculacion permiso, Collection<ITV> inspecciones) {
        LocalDate dia = LocalDate.ofInstant(fechaHora, URUGUAY);
        // Una inspección futura no desplaza a otra que sí cubre el día del evento.
        ITV itv = inspecciones.stream().filter(i -> !i.getFechaEmision().isAfter(dia))
                .max(Comparator.comparing(ITV::getFechaVencimiento))
                .orElseGet(() -> inspecciones.stream().min(Comparator.comparing(ITV::getFechaEmision))
                        .orElse(null));
        return new EvidenciaHabilitacion(matricula, fechaHora, permiso == null ? null : permiso.getNro(),
                permiso == null ? null : permiso.getFechaEmision(),
                permiso == null ? null : permiso.getFechaVencimiento(),
                itv == null ? null : itv.getFechaEmision(), itv == null ? null : itv.getFechaVencimiento());
    }

    public EvidenciaHabilitacion conOrigen(String origen) {
        if (!"TRACKING".equals(origen) && !"PESAJE".equals(origen)) {
            throw new IllegalArgumentException("Origen de evento inválido");
        }
        EvidenciaHabilitacion copia = new EvidenciaHabilitacion(matricula, fechaHora, nroPermiso,
                emisionPermiso, vencimientoPermiso, emisionItv, vencimientoItv);
        copia.origen = origen;
        return copia;
    }

    private EstadoDocumento estado(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) return EstadoDocumento.SIN_REGISTRO;
        LocalDate dia = LocalDate.ofInstant(fechaHora, URUGUAY);
        if (dia.isBefore(desde)) return EstadoDocumento.AUN_NO_VIGENTE;
        return dia.isAfter(hasta) ? EstadoDocumento.VENCIDO : EstadoDocumento.VIGENTE;
    }

    public boolean sinHabilitacion() {
        return getEstadoPermiso() != EstadoDocumento.VIGENTE || getEstadoItv() != EstadoDocumento.VIGENTE;
    }

    public String getMatricula() { return matricula; }
    public Instant getFechaHora() { return fechaHora; }
    public String getOrigen() { return origen; }
    public String getNroPermiso() { return nroPermiso; }
    public LocalDate getEmisionPermiso() { return emisionPermiso; }
    public LocalDate getVencimientoPermiso() { return vencimientoPermiso; }
    public LocalDate getEmisionItv() { return emisionItv; }
    public LocalDate getVencimientoItv() { return vencimientoItv; }
    public EstadoDocumento getEstadoPermiso() { return estado(emisionPermiso, vencimientoPermiso); }
    public EstadoDocumento getEstadoItv() { return estado(emisionItv, vencimientoItv); }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof EvidenciaHabilitacion p && Objects.equals(matricula, p.matricula)
                && Objects.equals(fechaHora, p.fechaHora) && Objects.equals(origen, p.origen)
                && Objects.equals(nroPermiso, p.nroPermiso) && Objects.equals(emisionPermiso, p.emisionPermiso)
                && Objects.equals(vencimientoPermiso, p.vencimientoPermiso)
                && Objects.equals(emisionItv, p.emisionItv) && Objects.equals(vencimientoItv, p.vencimientoItv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matricula, fechaHora, origen, nroPermiso, emisionPermiso,
                vencimientoPermiso, emisionItv, vencimientoItv);
    }
}
