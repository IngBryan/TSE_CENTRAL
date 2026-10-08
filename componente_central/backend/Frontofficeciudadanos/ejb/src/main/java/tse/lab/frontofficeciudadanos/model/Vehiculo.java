package tse.lab.frontofficeciudadanos.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;

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
