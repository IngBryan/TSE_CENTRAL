package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Locale;

// La matricula se da de alta normalizada (normalizarMatricula): es la clave con la que llegan los
// pesajes de las balanzas y las posiciones del tracking (p. ej. SBC1234).
// activo: la baja es logica; guias, ITV y permiso lo referencian y la fiscalizacion necesita su historia.
@Entity
@Table(name = "vehiculos")
public class Vehiculo implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String matricula;
    private String marca;
    private String modelo;
    private long peso;
    private long capacidadCargaKg;
    // default en la columna: los INSERT de datos de prueba que no lo mencionan quedan activos
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo = true;
    @ManyToOne
    @JoinColumn(name = "nro_empresa")
    private Empresa empresa;

    public Vehiculo() {
    }

    public Vehiculo(String matricula, String marca, String modelo, long peso, long capacidadCargaKg, Empresa empresa) {
        this.matricula = matricula;
        this.marca = marca;
        this.modelo = modelo;
        this.peso = peso;
        this.capacidadCargaKg = capacidadCargaKg;
        this.empresa = empresa;
    }

    // Mayusculas, sin espacios ni guiones: "sba 1234" y "SBA-1234" son SBA1234.
    public static String normalizarMatricula(String matricula) {
        return matricula == null ? null : matricula.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public String getMatricula() {
        return matricula;
    }

    public String getMarca() {
        return marca;
    }

    public String getModelo() {
        return modelo;
    }

    public long getPeso() {
        return peso;
    }

    public long getCapacidadCargaKg() {
        return capacidadCargaKg;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public void setPeso(long peso) {
        this.peso = peso;
    }

    public void setCapacidadCargaKg(long capacidadCargaKg) {
        this.capacidadCargaKg = capacidadCargaKg;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }
}
