package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidacionUsuarioTest {

    @ParameterizedTest
    @ValueSource(strings = { "ana@mtop.gub.uy", "a@b.c", "a.b@c.d", " ana@mtop.gub.uy " })
    void aceptaEmailsValidos(String email) {
        assertEquals(email.strip(), ValidacionUsuario.email(email));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "sin-arroba", "@mtop.gub.uy", "ana@", "ana@mtop", "ana@.uy", "ana@mtop.",
            "ana@@mtop.gub.uy", "a@b@c.d", "ana maria@mtop.gub.uy", "ana@mtop .gub.uy" })
    void rechazaEmailsInvalidos(String email) {
        assertThrows(DatosUsuarioInvalidosException.class, () -> ValidacionUsuario.email(email));
    }
}
