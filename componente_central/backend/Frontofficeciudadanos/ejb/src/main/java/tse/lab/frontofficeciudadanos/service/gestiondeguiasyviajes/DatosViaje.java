package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

// estado: nombre de EstadoGuia (ASIGNADA o EN_CURSO); va como texto para no depender de Modelo
public record DatosViaje(String idGuia, String matricula, Long nroEmpresa, String estado, LocalDate fecha,
        Instant fechaAsignacion, String origen, String destino, long pesoCargaKg) implements Serializable {
}
