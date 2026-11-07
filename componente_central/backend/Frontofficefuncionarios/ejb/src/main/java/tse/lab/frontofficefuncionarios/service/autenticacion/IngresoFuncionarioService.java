package tse.lab.frontofficefuncionarios.service.autenticacion;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.adaptadorpdi.ClientePdi;
import tse.lab.adaptadorpdi.DatosPersonaPdi;
import tse.lab.adaptadorpdi.MayoriaDeEdad;
import tse.lab.adaptadorpdi.PdiNoDisponibleException;
import tse.lab.frontofficefuncionarios.service.LogFuncionarios;
import tse.lab.model.Funcionario;

// Ingreso de funcionarios con Usuario gub.uy (o, en testing, con la cedula). El OIDC y la sesion los hace
// el frontoffice de Next; este servicio recibe la cedula que gub.uy ya autentico y:
// 1. controla que este registrada como funcionario del MTOP (si no, no hay sesion);
// 2. la valida contra la PDI (DNIC): que exista y sea mayor de edad, y toma su nombre.
// Si la PDI no responde se deja entrar igual (el funcionario ya esta registrado en carga.uy) con el
// resultado NO_DISPONIBLE, como el FA8 del ingreso de ciudadanos: la PDI caida no deja sin servicio al
// frontoffice. Cada operacion sigue controlando que sea funcionario (AutorizacionFuncionario).
@Stateless
public class IngresoFuncionarioService {

    private static final Logger LOG = Logger.getLogger(IngresoFuncionarioService.class.getName());

    public enum Resultado {
        VALIDADO,
        NO_ES_FUNCIONARIO,
        NO_EXISTE,
        MENOR_DE_EDAD,
        NO_DISPONIBLE
    }

    public record ResultadoIngreso(Resultado resultado, boolean sesionPermitida, String cedula, String email,
            String nombres, String apellidos) {
    }

    @EJB
    private FuncionarioDAO funcionarios;

    @Inject
    private ClientePdi clientePdi;

    public ResultadoIngreso ingresarConGubUy(String cedula, String email, String correlationId) {
        return ingresar(cedula, email, correlationId, "gubuy");
    }

    // Ingreso de testing (FUNCIONARIOS_INGRESO_TESTING=true): los mismos controles con la cedula ingresada a mano
    public ResultadoIngreso ingresarParaTesting(String cedula, String correlationId) {
        return ingresar(cedula, null, correlationId, "testing");
    }

    private ResultadoIngreso ingresar(String cedula, String email, String correlationId, String via) {
        Funcionario funcionario = funcionarios.buscarPorCi(cedula);
        // Inactivo: un administrador le quito el rol (backoffice); se conserva solo por su historial
        if (funcionario == null || !funcionario.isActivo()) {
            return log(via, correlationId, new ResultadoIngreso(Resultado.NO_ES_FUNCIONARIO, false, cedula, email,
                    null, null));
        }
        // El email de gub.uy es el verificado: se guarda para las notificaciones al funcionario
        if (email != null && !email.isBlank()) {
            funcionario.setEmail(email);
        }

        Optional<DatosPersonaPdi> datos;
        try {
            datos = clientePdi.consultarPersona(cedula, correlationId);
        } catch (PdiNoDisponibleException e) {
            return log(via, correlationId, new ResultadoIngreso(Resultado.NO_DISPONIBLE, true, cedula,
                    funcionario.getEmail(), null, null));
        }

        if (datos.isEmpty()) {
            return log(via, correlationId, new ResultadoIngreso(Resultado.NO_EXISTE, false, cedula,
                    funcionario.getEmail(), null, null));
        }
        DatosPersonaPdi persona = datos.get();
        Resultado resultado = MayoriaDeEdad.cumple(persona.fechaNacimiento()) ? Resultado.VALIDADO
                : Resultado.MENOR_DE_EDAD;
        return log(via, correlationId, new ResultadoIngreso(resultado, resultado == Resultado.VALIDADO, cedula,
                funcionario.getEmail(), persona.nombres(), persona.apellidos()));
    }

    private static ResultadoIngreso log(String via, String correlationId, ResultadoIngreso r) {
        LogFuncionarios.evento("ingreso_funcionario." + via)
                .nivel(r.sesionPermitida() && r.resultado() == Resultado.VALIDADO ? Level.INFO : Level.WARNING)
                .correlationId(correlationId).campo("ci", r.cedula()).campo("resultado", r.resultado())
                .campo("sesion", r.sesionPermitida()).escribirEn(LOG);
        return r;
    }
}
