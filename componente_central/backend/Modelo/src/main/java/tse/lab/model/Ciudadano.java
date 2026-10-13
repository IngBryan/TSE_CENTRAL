package tse.lab.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Ciudadano de carga.uy, identificado por su cedula: es un actor en si mismo y tiene los datos comunes
// de la persona. Sus roles (Responsable de una empresa, Chofer de una o varias) son otras tablas que lo
// referencian por ci. Lo crea el primer ingreso con gub.uy (CU-01) o el alta de un rol (por ejemplo
// CU-02, sin cuenta gub.uy todavia), y lo completa la validacion contra la PDI (CU-03): nombres,
// apellidos y fecha de nacimiento salen de DNIC.
@Entity
@Table(name = "ciudadanos")
public class Ciudadano implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String ci;
    private String email;
    // sub de ID Uruguay; null si el registro se creo antes de que la persona ingresara (CU-02)
    @Column(unique = true)
    private String subGubUy;
    private String nombres;
    private String apellidos;
    // Lo unico que se le muestra a un tercero (CU-02, Ley 18.331)
    private String primerNombre;
    private String primerApellido;
    private LocalDate fechaNacimiento;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoValidacion estado;
    private Instant creado;
    private Instant ultimoIngreso;
    private Instant ultimaValidacion;

    public Ciudadano() {
    }

    public Ciudadano(String ci, String email, String subGubUy) {
        this.ci = ci;
        this.email = email;
        this.subGubUy = subGubUy;
        this.estado = EstadoValidacion.PENDIENTE_VALIDACION;
        this.creado = Instant.now();
    }

    public String getCi() {
        return ci;
    }

    public void setCi(String ci) {
        this.ci = ci;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSubGubUy() {
        return subGubUy;
    }

    public void setSubGubUy(String subGubUy) {
        this.subGubUy = subGubUy;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getPrimerNombre() {
        return primerNombre;
    }

    public void setPrimerNombre(String primerNombre) {
        this.primerNombre = primerNombre;
    }

    public String getPrimerApellido() {
        return primerApellido;
    }

    public void setPrimerApellido(String primerApellido) {
        this.primerApellido = primerApellido;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public EstadoValidacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoValidacion estado) {
        this.estado = estado;
    }

    public Instant getCreado() {
        return creado;
    }

    public Instant getUltimoIngreso() {
        return ultimoIngreso;
    }

    public void setUltimoIngreso(Instant ultimoIngreso) {
        this.ultimoIngreso = ultimoIngreso;
    }

    public Instant getUltimaValidacion() {
        return ultimaValidacion;
    }

    public void setUltimaValidacion(Instant ultimaValidacion) {
        this.ultimaValidacion = ultimaValidacion;
    }
}
