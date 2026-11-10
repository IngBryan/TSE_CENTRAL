package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.io.Serializable;

// La contrasena almacena el hash producido por la capa de autenticacion.
@Entity
@Table(name = "usuarios_backoffice")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class UsuarioBackoffice implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String ci;

    @Column(nullable = false)
    private String contrasena;

    // La contrasena la definio un administrador (alta o cambio de rol): hasta que el usuario ponga
    // la suya, el backoffice solo le deja cambiarla. Con default: los INSERT sin la columna quedan en false.
    @Column(name = "debe_cambiar_contrasena", columnDefinition = "boolean default false not null")
    private boolean debeCambiarContrasena;

    public UsuarioBackoffice() {
    }

    public UsuarioBackoffice(String ci, String contrasena) {
        this.ci = ci;
        this.contrasena = contrasena;
    }

    public String getCi() {
        return ci;
    }

    public void setCi(String ci) {
        this.ci = ci;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public boolean isDebeCambiarContrasena() {
        return debeCambiarContrasena;
    }

    public void setDebeCambiarContrasena(boolean debeCambiarContrasena) {
        this.debeCambiarContrasena = debeCambiarContrasena;
    }
}

