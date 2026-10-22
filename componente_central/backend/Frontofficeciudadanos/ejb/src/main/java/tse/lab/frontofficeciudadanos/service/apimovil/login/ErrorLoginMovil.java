package tse.lab.frontofficeciudadanos.service.apimovil.login;

// Motivo por el que no se pudo completar el login de la app. El motivo viaja a la app en
// { "error": "<motivo>" } y la app muestra el mensaje que corresponde; el detalle solo va al log.
public class ErrorLoginMovil extends Exception {

    public enum Motivo {
        SOLICITUD_INVALIDA("solicitud_invalida", 400),
        REDIRECT_INVALIDO("redirect_invalido", 400),
        // code rechazado por gub.uy, firma/iss/aud/nonce del id_token invalidos o sub distinto
        TOKEN_INVALIDO("token_invalido", 400),
        // la PDI dice que la cedula no existe (CU-01 FA6) o que es menor de edad (FA7)
        NO_EXISTE("no_existe", 403),
        MENOR_DE_EDAD("menor_de_edad", 403),
        NO_CHOFER("no_chofer", 403),
        // gub.uy no responde, timeout o 5xx
        IDP_NO_DISPONIBLE("idp_no_disponible", 503),
        // falta configuracion del servidor (client id/secret, MOVIL_TOKEN_SECRET)
        INGRESO_NO_DISPONIBLE("ingreso_no_disponible", 503);

        private final String codigo;
        private final int status;

        Motivo(String codigo, int status) {
            this.codigo = codigo;
            this.status = status;
        }

        public String codigo() {
            return codigo;
        }

        public int status() {
            return status;
        }
    }

    private final Motivo motivo;

    public ErrorLoginMovil(Motivo motivo, String detalle) {
        super(detalle);
        this.motivo = motivo;
    }

    public ErrorLoginMovil(Motivo motivo, String detalle, Throwable causa) {
        super(detalle, causa);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
