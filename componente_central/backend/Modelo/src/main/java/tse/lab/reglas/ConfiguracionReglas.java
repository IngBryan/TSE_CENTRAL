package tse.lab.reglas;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Snapshot inmutable. Clase (no record) para JBoss Marshalling/EJB remoto. */
public final class ConfiguracionReglas implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String EXCHANGE = "fiscalizacion.config";
    private final long revision;
    private final BigDecimal toleranciaCargaPorcentaje;
    private final int toleranciaCargaKg, distanciaDesvioMetros, ventanaDesvioSegundos;
    private final String fechaActualizacion, usuario, fundamento;
    private final boolean publicada;

    public ConfiguracionReglas(long revision, BigDecimal porcentaje, int kg, int metros, int segundos,
            String fecha, String usuario, String fundamento, boolean publicada) {
        if (revision < 1) throw new IllegalArgumentException("Revisión inválida");
        if (porcentaje == null || porcentaje.signum() < 0 || porcentaje.compareTo(new BigDecimal("20")) > 0
                || porcentaje.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("La tolerancia porcentual debe estar entre 0 y 20, con hasta dos decimales");
        if (kg < 0 || kg > 5000) throw new IllegalArgumentException("El margen de carga debe estar entre 0 y 5000 kg");
        if (metros < 1 || metros > 10000) throw new IllegalArgumentException("La distancia debe estar entre 1 y 10000 metros");
        if (segundos < 1 || segundos > 600) throw new IllegalArgumentException("La ventana debe estar entre 1 y 600 segundos");
        String fechaValidada = Instant.parse(Objects.requireNonNull(fecha)).toString();
        this.revision = revision;
        this.toleranciaCargaPorcentaje = porcentaje;
        this.toleranciaCargaKg = kg;
        this.distanciaDesvioMetros = metros;
        this.ventanaDesvioSegundos = segundos;
        this.fechaActualizacion = fechaValidada;
        this.usuario = Objects.requireNonNull(usuario);
        this.fundamento = Objects.requireNonNull(fundamento);
        this.publicada = publicada;
    }
    /** Solo para tests/constructores compatibles; producción siempre carga la revisión persistida. */
    public static ConfiguracionReglas inicial() {
        return new ConfiguracionReglas(1, BigDecimal.ZERO, 0, 2000, 30,
                Instant.EPOCH.toString(), "SISTEMA", "Configuración inicial", false);
    }
    public long getRevision() { return revision; }
    public BigDecimal getToleranciaCargaPorcentaje() { return toleranciaCargaPorcentaje; }
    public int getToleranciaCargaKg() { return toleranciaCargaKg; }
    public int getDistanciaDesvioMetros() { return distanciaDesvioMetros; }
    public int getVentanaDesvioSegundos() { return ventanaDesvioSegundos; }
    public String getFechaActualizacion() { return fechaActualizacion; }
    public String getUsuario() { return usuario; }
    public String getFundamento() { return fundamento; }
    public boolean isPublicada() { return publicada; }
    public BigDecimal limiteCargaKg(long baseKg) {
        BigDecimal base = BigDecimal.valueOf(baseKg);
        BigDecimal margen = base.multiply(toleranciaCargaPorcentaje).movePointLeft(2)
                .max(BigDecimal.valueOf(toleranciaCargaKg));
        return base.add(margen);
    }
}
