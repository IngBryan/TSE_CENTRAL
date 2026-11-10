package tse.lab.backofficeautoridadesadministradores.service.autenticacion;

/**
 * Estado vigente de un usuario del backoffice, leído de la base en el login
 * y en cada pedido: su rol y si todavía tiene que cambiar la contraseña que
 * le definió un administrador. No contiene la contraseña ni su hash.
 *
 * @param rol ADMINISTRADOR o AUTORIDAD
 */
public record CuentaBackoffice(
        String ci,
        String rol,
        boolean debeCambiarContrasena) {

    /** Lo que va en el token: el estado del cambio de contraseña se lee siempre de la base. */
    public IdentidadBackoffice identidad() {
        return new IdentidadBackoffice(ci, rol);
    }
}
