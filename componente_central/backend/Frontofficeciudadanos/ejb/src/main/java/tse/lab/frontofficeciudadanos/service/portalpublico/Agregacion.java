package tse.lab.frontofficeciudadanos.service.portalpublico;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Regla de agregacion de la informacion publica (Ley 18.331): un numero se publica solo si junta datos de al
// menos `umbral` empresas distintas. Con 2, cualquiera de las dos restando sus propios datos obtiene los de la
// otra; con 3 o mas ya no. Vehiculos y choferes quedan cubiertos porque pertenecen a una empresa.
//
// Supresion complementaria: si en una tabla se reservan filas y se publica el total, la suma de las reservadas
// se deduce restando. Por eso se reservan filas visibles adicionales (las mas chicas) hasta que el conjunto de
// las reservadas tambien junte al menos `umbral` empresas. Una fila con cantidad 0 se publica: no identifica.
// Sin estado: se prueba sin levantar nada.
public final class Agregacion {

    // clave: lo que identifica la fila (mes, rubro, tipo de infraccion); empresas: las que aportan datos
    public record Fila(String clave, String etiqueta, Set<Long> empresas, long cantidad, long kg) {
    }

    // cantidad y kg en null cuando la fila esta reservada
    public record FilaPublica(String clave, String etiqueta, Long cantidad, Long kg, boolean reservado) {
    }

    private Agregacion() {
    }

    static List<FilaPublica> proteger(List<Fila> filas, int umbral) {
        Set<Fila> reservadas = reservadas(filas, umbral);
        return filas.stream()
                .map(f -> reservadas.contains(f)
                        ? new FilaPublica(f.clave(), f.etiqueta(), null, null, true)
                        : new FilaPublica(f.clave(), f.etiqueta(), f.cantidad(), f.kg(), false))
                .toList();
    }

    // Para filas cuya etiqueta puede identificar a una empresa (un rubro con pocas empresas): las
    // reservadas no se muestran una por una, sino sumadas en una sola fila al final (clave, etiqueta). Esa
    // fila se publica si junta `umbral` empresas (lo que pasa siempre que alcanzo la supresion complementaria:
    // de todos modos se deduce como total menos lo visible); si no, queda reservada.
    static List<FilaPublica> protegerAgrupando(List<Fila> filas, int umbral, String clave, String etiqueta) {
        Set<Fila> reservadas = reservadas(filas, umbral);
        List<FilaPublica> publicas = new ArrayList<>(filas.stream()
                .filter(f -> !reservadas.contains(f))
                .map(f -> new FilaPublica(f.clave(), f.etiqueta(), f.cantidad(), f.kg(), false))
                .toList());
        if (!reservadas.isEmpty()) {
            long cantidad = reservadas.stream().mapToLong(Fila::cantidad).sum();
            long kg = reservadas.stream().mapToLong(Fila::kg).sum();
            publicas.add(empresas(reservadas).size() >= umbral
                    ? new FilaPublica(clave, etiqueta, cantidad, kg, false)
                    : new FilaPublica(clave, etiqueta, null, null, true));
        }
        return publicas;
    }

    private static Set<Fila> reservadas(List<Fila> filas, int umbral) {
        Set<Fila> reservadas = new HashSet<>();
        for (Fila f : filas) {
            if (f.cantidad() > 0 && f.empresas().size() < umbral) {
                reservadas.add(f);
            }
        }
        List<Fila> visibles = new ArrayList<>(filas.stream()
                .filter(f -> f.cantidad() > 0 && !reservadas.contains(f))
                .sorted(Comparator.comparingLong(Fila::cantidad).thenComparingInt(f -> f.empresas().size()))
                .toList());
        while (!reservadas.isEmpty() && empresas(reservadas).size() < umbral && !visibles.isEmpty()) {
            reservadas.add(visibles.remove(0));
        }
        return reservadas;
    }

    // Un total suelto (sin filas que lo desagreguen)
    static boolean publicable(Set<Long> empresas, long cantidad, int umbral) {
        return cantidad == 0 || empresas.size() >= umbral;
    }

    private static Set<Long> empresas(Set<Fila> filas) {
        Set<Long> todas = new HashSet<>();
        filas.forEach(f -> todas.addAll(f.empresas()));
        return todas;
    }
}
