package tse.lab.model;

import java.util.Set;

// Ciclo de vida de un caso de fiscalizacion (letra, seccion 3.5): detectada -> notificada -> en descargo
// -> confirmada | desestimada. Las transiciones previstas estan solo aca; CasoFiscalizacion.cambiarEstado
// rechaza cualquier otra, sin importar que modulo lo llame.
public enum EstadoCaso {
    DETECTADA,
    NOTIFICADA,
    EN_DESCARGO,
    CONFIRMADA,
    DESESTIMADA;

    public Set<EstadoCaso> siguientes() {
        return switch (this) {
            case DETECTADA -> Set.of(NOTIFICADA);
            case NOTIFICADA -> Set.of(EN_DESCARGO);
            case EN_DESCARGO -> Set.of(CONFIRMADA, DESESTIMADA);
            case CONFIRMADA, DESESTIMADA -> Set.of();
        };
    }

    public boolean puedePasarA(EstadoCaso nuevo) {
        return nuevo != null && siguientes().contains(nuevo);
    }

    // Un funcionario resuelve el caso cuando puede confirmarlo o desestimarlo
    public boolean admiteResolucion() {
        return puedePasarA(CONFIRMADA) && puedePasarA(DESESTIMADA);
    }

    public boolean esFinal() {
        return siguientes().isEmpty();
    }

    // Abierto: todavia no paso a descargo ni se resolvio. La fiscalizacion automatica no crea otro caso del
    // mismo tipo para el vehiculo mientras haya uno abierto: le asocia los eventos nuevos.
    public static final Set<EstadoCaso> ABIERTOS = Set.of(DETECTADA, NOTIFICADA);

    public boolean esAbierto() {
        return ABIERTOS.contains(this);
    }
}
