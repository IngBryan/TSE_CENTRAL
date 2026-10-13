package tse.lab.frontofficeciudadanos.service.adaptadorpdi;

import java.time.LocalDate;

// Lo que carga.uy usa de la respuesta obtDocDigitalizado de DNIC. primerNombre y primerApellido
// van aparte porque son lo unico que se le muestra a un tercero (CU-02, Ley 18.331).
public record DatosPersonaPdi(String primerNombre, String primerApellido, String nombres, String apellidos,
        LocalDate fechaNacimiento) {
}
