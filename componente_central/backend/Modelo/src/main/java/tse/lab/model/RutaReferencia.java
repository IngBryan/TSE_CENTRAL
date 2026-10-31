package tse.lab.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Geometria de ruta compartida e inmutable. El GeoJSON debe estar normalizado antes
 * de construirla (se definira en el servicio de enrutamiento, tarea 2).
 *
 * La huella evita duplicar una misma representacion canonica de LineString.
 * Nunca actualizar la geometria de un registro existente: crear otro registro.
 */
@Entity
@Table(name = "rutas_referencia", uniqueConstraints =
        @UniqueConstraint(name = "uk_ruta_referencia_sha256", columnNames = "sha256_geojson"))
public class RutaReferencia implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sha256_geojson", nullable = false, updatable = false, length = 64)
    private String sha256GeoJson;

    @Column(name = "geometria_geojson", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String geometriaGeoJson;

    @Column(name = "distancia_metros", nullable = false, updatable = false)
    private double distanciaMetros;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    protected RutaReferencia() { }

    public RutaReferencia(String geoJsonCanonico, double distanciaMetros) {
        if (geoJsonCanonico == null || geoJsonCanonico.isBlank()) {
            throw new IllegalArgumentException("La geometria GeoJSON es obligatoria");
        }
        if (!Double.isFinite(distanciaMetros) || distanciaMetros <= 0) {
            throw new IllegalArgumentException("La distancia debe ser positiva y finita");
        }
        this.geometriaGeoJson = geoJsonCanonico;
        this.sha256GeoJson = calcularHuella(geoJsonCanonico);
        this.distanciaMetros = distanciaMetros;
        this.fechaCreacion = Instant.now();
    }

    public static String calcularHuella(String geoJsonCanonico) {
        Objects.requireNonNull(geoJsonCanonico, "geoJsonCanonico");
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(geoJsonCanonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public Long getId() { return id; }
    public String getSha256GeoJson() { return sha256GeoJson; }
    public String getGeometriaGeoJson() { return geometriaGeoJson; }
    public double getDistanciaMetros() { return distanciaMetros; }
    public Instant getFechaCreacion() { return fechaCreacion; }
}
