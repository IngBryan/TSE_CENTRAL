package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.time.Instant;
import java.util.List;

// Operaciones de la vista remota (PesajesRemote).
public interface Pesajes {

    // Guarda los pesajes pendientes de deteccion. Ignora los que ya existen (misma matricula y fecha y
    // hora) y los de balanzas que no estan dadas de alta. Devuelve cuantos guardo.
    int guardarPesajes(List<DatosPesaje> pesajes);

    // Pesajes pendientes de deteccion, del mas viejo al mas nuevo
    List<DatosPesaje> listarPendientes(int limite);

    void marcarDetectado(String matricula, Instant fechaHora);
}
