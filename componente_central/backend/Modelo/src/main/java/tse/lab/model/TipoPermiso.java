package tse.lab.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Permiso adicional a demanda, ademas del Permiso Nacional de Circulacion y la ITV, que siguen siendo fijos y
// para todos. Lo da de alta un administrador y un funcionario lo asigna a las empresas que deben tenerlo.
// activo: la baja es logica; desactivado deja de exigirse, pero se conserva lo cargado en cada vehiculo.
@Entity
@Table(name = "tipos_permiso")
public class TipoPermiso implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, nullable = false, unique = true)
    private String nombre;
    private boolean pideNumero;
    private boolean pideVencimiento;
    @Column(nullable = false)
    private boolean activo = true;

    public TipoPermiso() {
    }

    public TipoPermiso(String nombre, boolean pideNumero, boolean pideVencimiento) {
        this.nombre = nombre;
        this.pideNumero = pideNumero;
        this.pideVencimiento = pideVencimiento;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isPideNumero() {
        return pideNumero;
    }

    public void setPideNumero(boolean pideNumero) {
        this.pideNumero = pideNumero;
    }

    public boolean isPideVencimiento() {
        return pideVencimiento;
    }

    public void setPideVencimiento(boolean pideVencimiento) {
        this.pideVencimiento = pideVencimiento;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
