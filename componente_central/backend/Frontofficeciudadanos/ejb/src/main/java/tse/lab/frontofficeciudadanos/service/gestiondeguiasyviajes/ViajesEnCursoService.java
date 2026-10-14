package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;

// Viajes en curso de un vehiculo para la fiscalizacion automatica: ASIGNADA cuenta como en curso
// porque el chofer puede salir antes de marcar el INICIO en la app movil.
@Stateless
public class ViajesEnCursoService implements ViajesEnCursoRemote {

    private static final List<EstadoGuia> EN_CURSO = List.of(EstadoGuia.ASIGNADA, EstadoGuia.EN_CURSO);

    @EJB
    private GuiaDAO guias;

    @Override
    public List<DatosViaje> buscarViajesEnCurso(String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return List.of();
        }
        return guias.buscarPorVehiculoYEstados(matricula.strip(), EN_CURSO).stream()
                .map(ViajesEnCursoService::datos)
                .toList();
    }

    private static DatosViaje datos(Guia g) {
        return new DatosViaje(g.getId(), g.getVehiculo().getMatricula(), g.getEmpresa().getNroEmpresa(),
                g.getEstado().name(), g.getFecha(), g.getFechaAsignacion(), g.getOrigen(), g.getDestino(),
                g.getPesoCargaKg());
    }
}
