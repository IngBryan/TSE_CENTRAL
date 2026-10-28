package tse.lab.frontofficefuncionarios.data;

import java.time.Instant;

import tse.lab.model.ResultadoEventoViaje;
import tse.lab.model.TipoEventoViaje;

// Un EventoViaje sin la foto (hasta 5 MB cada una): para la linea de tiempo alcanza con saber si tiene.
// La foto se pide aparte (EventoViajeDAO.foto). conFoto es Boolean porque asi lo tipa el CASE de la consulta.
public record EventoViajeFila(String idEvento, TipoEventoViaje tipo, String ciChofer, Instant fechaHoraDispositivo,
        Instant fechaRecepcion, String descripcion, Double latitud, Double longitud, Boolean conFoto,
        ResultadoEventoViaje resultado, String codigo, String detalle) {
}
