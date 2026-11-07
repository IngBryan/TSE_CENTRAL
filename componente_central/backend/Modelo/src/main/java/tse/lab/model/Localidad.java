package tse.lab.model;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Localidad uruguaya del catalogo; coordenadas de referencia WGS84 para enrutamiento. */
@Entity
@Table(name = "localidades", uniqueConstraints =
        @UniqueConstraint(name = "uk_localidad_nombre_departamento", columnNames = {"nombre", "departamento"}))
public class Localidad implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String departamento;

    @Column(nullable = false)
    private double latitud;

    @Column(nullable = false)
    private double longitud;

    protected Localidad() { }

    public Localidad(Long id, String nombre, String departamento, double latitud, double longitud) {
        if (id == null || id <= 0 || nombre == null || nombre.isBlank()
                || departamento == null || departamento.isBlank()) {
            throw new IllegalArgumentException("Localidad requiere identificador, nombre y departamento");
        }
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90
                || !Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("Coordenadas WGS84 invalidas");
        }
        this.id = id;
        this.nombre = nombre.trim();
        this.departamento = departamento.trim();
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDepartamento() { return departamento; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
}
