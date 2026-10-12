package tse.lab.frontofficeciudadanos.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Evento de un viaje registrado por el chofer desde la app movil (inicio, fin, cargas y descargas
// parciales, incidentes). La clave es el idEvento (UUID) que genera el movil: si el movil reenvia el
// mismo evento (reintento tras un corte de red) la clave primaria lo detecta y no se aplica dos
// veces. Se guardan todos los eventos con datos validos, tambien los que no se pudieron aplicar
// (CONFLICTO, ver ResultadoEventoViaje), con el resultado, el estado y la version en que dejaron la
// guia: asi un reenvio recibe la misma respuesta que el envio original y el responsable ve el historial.
@Entity
@Table(name = "eventos_viaje")
public class EventoViaje implements Serializable {
    private static final long serialVersionUID = 1L;

    // 5 MB de foto ya comprimida por el movil
    public static final int MAX_FOTO_BYTES = 5 * 1024 * 1024;

    @Id
    @Column(length = 36)
    private String idEvento;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_guia")
    private Guia guia;
    @Column(nullable = false)
    private String ciChofer;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEventoViaje tipo;
    // Hora del dispositivo (puede ser muy anterior a la recepcion si el chofer estaba sin conexion)
    @Column(nullable = false)
    private Instant fechaHoraDispositivo;
    @Column(nullable = false)
    private Instant fechaRecepcion;
    @Column(length = 500)
    private String descripcion;
    private Double latitud;
    private Double longitud;
    @Column(length = MAX_FOTO_BYTES)
    private byte[] foto;
    // Version de la guia que conocia el movil al enviar el evento (deteccion de guia modificada)
    private Long versionGuiaDispositivo;
    @Enumerated(EnumType.STRING)
    private ResultadoEventoViaje resultado;
    // Codigo y explicacion del resultado (p. ej. VIAJE_FINALIZADO); null si se aplico normalmente
    private String codigo;
    @Column(length = 200)
    private String detalle;
    @Enumerated(EnumType.STRING)
    private EstadoGuia estadoGuiaResultante;
    private Long versionGuiaResultante;

    public EventoViaje() {
    }

    public EventoViaje(String idEvento, Guia guia, String ciChofer, TipoEventoViaje tipo, Instant fechaHoraDispositivo,
            String descripcion, Double latitud, Double longitud, byte[] foto) {
        this.idEvento = idEvento;
        this.guia = guia;
        this.ciChofer = ciChofer;
        this.tipo = tipo;
        this.fechaHoraDispositivo = fechaHoraDispositivo;
        this.fechaRecepcion = Instant.now().truncatedTo(ChronoUnit.MICROS); // la precision que guarda la base
        this.descripcion = descripcion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.foto = foto;
    }

    public String getIdEvento() {
        return idEvento;
    }

    public Guia getGuia() {
        return guia;
    }

    public String getCiChofer() {
        return ciChofer;
    }

    public TipoEventoViaje getTipo() {
        return tipo;
    }

    public Instant getFechaHoraDispositivo() {
        return fechaHoraDispositivo;
    }

    public Instant getFechaRecepcion() {
        return fechaRecepcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Double getLatitud() {
        return latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public byte[] getFoto() {
        return foto;
    }

    public EstadoGuia getEstadoGuiaResultante() {
        return estadoGuiaResultante;
    }

    public Long getVersionGuiaResultante() {
        return versionGuiaResultante;
    }

    public Long getVersionGuiaDispositivo() {
        return versionGuiaDispositivo;
    }

    public void setVersionGuiaDispositivo(Long versionGuiaDispositivo) {
        this.versionGuiaDispositivo = versionGuiaDispositivo;
    }

    public ResultadoEventoViaje getResultado() {
        return resultado;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDetalle() {
        return detalle;
    }

    public void registrarResultado(ResultadoEventoViaje resultado, String codigo, String detalle, EstadoGuia estado,
            Long version) {
        this.resultado = resultado;
        this.codigo = codigo;
        this.detalle = detalle;
        this.estadoGuiaResultante = estado;
        this.versionGuiaResultante = version;
    }
}
