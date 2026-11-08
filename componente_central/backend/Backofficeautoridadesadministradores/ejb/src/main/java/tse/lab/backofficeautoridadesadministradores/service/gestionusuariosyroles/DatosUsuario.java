package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.List;

/**
 * Una persona en el listado de usuarios, con todos sus roles.
 *
 * Nombres y apellidos solo existen si la persona también es un ciudadano
 * validado: en el resto de los casos son null. Por minimización (Ley 18.331)
 * no incluye fecha de nacimiento, sub de gub.uy ni contraseña.
 */
public record DatosUsuario(
        String ci,
        String nombres,
        String apellidos,
        String email,
        List<RolUsuario> roles) {
}
