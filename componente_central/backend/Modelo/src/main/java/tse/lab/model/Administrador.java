package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;


@Entity
@Table(name = "administradores")
public class Administrador extends UsuarioBackoffice {
    private static final long serialVersionUID = 1L;

    public Administrador() {
    }

    public Administrador(String ci, String contrasena) {
        super(ci, contrasena);
    }
}

