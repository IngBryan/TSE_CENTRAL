package tse.lab.frontofficeciudadanos.servicio;

// Respuesta de los dos pasos de CU-02. Por la Ley 18.331 solo lleva primer nombre y primer
// apellido; nunca la fecha de nacimiento ni el resto de los datos de DNIC.
public record ResultadoAltaChofer(Resultado resultado, String cedula, String primerNombre, String primerApellido,
        String estado) {

    public enum Resultado {
        CONFIRMAR, // pasos 6-7 (o FA2): validado, falta que el responsable confirme el nombre
        YA_ES_CHOFER, // FA1
        NO_EXISTE, // FA4
        MENOR_DE_EDAD, // FA4
        ASOCIADO_PENDIENTE, // FA5: la PDI no respondio, queda asociado pendiente de validacion
        ALTA_CONFIRMADA, // pasos 9-10
        DEBE_CONSULTAR // se pidio confirmar sin una consulta previa valida
    }

    static ResultadoAltaChofer de(Resultado resultado, String cedula) {
        return new ResultadoAltaChofer(resultado, cedula, null, null, null);
    }
}
