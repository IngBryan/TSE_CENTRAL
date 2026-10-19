package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;


@Entity
@Table(name = "autoridades")
public class Autoridad extends UsuarioBackoffice {
    private static final long serialVersionUID = 1L;

    public Autoridad() {
    }

    public Autoridad(String ci, String contrasena) {
        super(ci, contrasena);
    }
}

