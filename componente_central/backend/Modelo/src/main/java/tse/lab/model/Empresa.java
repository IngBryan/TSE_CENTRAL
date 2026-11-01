package tse.lab.model;

import java.io.Serializable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "empresas")
public class Empresa implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private Long nroEmpresa;
    private String nombrePublico;
    private String razonSocial;
    private String direccion;
    // Opcional e informativo (catalogo Rubro): no limita lo que la empresa puede transportar
    @Column(length = 100)
    private String rubro;
    // La habilita un funcionario del MTOP; solo una empresa habilitada puede dar de alta choferes (CU-02)
    private boolean habilitada;
    @OneToOne(mappedBy = "empresa", cascade = { CascadeType.PERSIST, CascadeType.MERGE }) // lo defini como one to one,
                                                                                          // comenten si les parece
    private Responsable responsable;
    @OneToMany(mappedBy = "empresa", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<Chofer> choferes;
    @OneToMany(mappedBy = "empresa", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<Guia> guias;
    @OneToMany(mappedBy = "empresa", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<Vehiculo> vehiculos;
    // Permisos a demanda que un funcionario le asigno: los tienen que tener todos sus vehiculos
    @ManyToMany
    @JoinTable(name = "empresas_tipos_permiso", joinColumns = @JoinColumn(name = "nro_empresa"),
            inverseJoinColumns = @JoinColumn(name = "id_tipo"))
    private Set<TipoPermiso> permisosRequeridos = new HashSet<>();
    // Bloqueo optimista: el funcionario manda la version que estaba mirando al modificar la empresa, para no
    // pisar sin aviso lo que otro cambio mientras tanto
    @Version
    private Long version;

    public Empresa() {
        this.choferes = new ArrayList<>();
        this.guias = new ArrayList<>();
        this.vehiculos = new ArrayList<>();
    }

    public Empresa(Long nroEmpresa, String nombrePublico, String razonSocial, String direccion,
            Responsable responsable) {
        this.nroEmpresa = nroEmpresa;
        this.nombrePublico = nombrePublico;
        this.razonSocial = razonSocial;
        this.direccion = direccion;
        this.responsable = responsable;
        this.choferes = new ArrayList<>(); /* inicialmente no tiene choferes asignados */
        this.guias = new ArrayList<>(); /* inicialmente no tiene guias asignadas */
        this.vehiculos = new ArrayList<>(); /* inicialmente no tiene vehiculos asignados */
    }

    public Long getNroEmpresa() {
        return nroEmpresa;
    }

    public String getNombrePublico() {
        return nombrePublico;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getRubro() {
        return rubro;
    }

    public void setRubro(String rubro) {
        this.rubro = rubro;
    }

    public boolean isHabilitada() {
        return habilitada;
    }

    public void setHabilitada(boolean habilitada) {
        this.habilitada = habilitada;
    }

    public Responsable getResponsable() {
        return responsable;
    }

    public List<Chofer> getChoferes() {
        return choferes;
    }

    public List<Guia> getGuias() {
        return guias;
    }

    public List<Vehiculo> getVehiculos() {
        return vehiculos;
    }

    public Set<TipoPermiso> getPermisosRequeridos() {
        return permisosRequeridos;
    }

    public Long getVersion() {
        return version;
    }

    public void setNroEmpresa(Long nroEmpresa) {
        this.nroEmpresa = nroEmpresa;
    }

    public void setNombrePublico(String nombrePublico) {
        this.nombrePublico = nombrePublico;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public void setResponsable(Responsable responsable) {
        this.responsable = responsable;
    }

    public void setChoferes(List<Chofer> choferes) {
        this.choferes = choferes;
    }

    public void setGuias(List<Guia> guias) {
        this.guias = guias;
    }

    public void setVehiculos(List<Vehiculo> vehiculos) {
        this.vehiculos = vehiculos;
    }
}
