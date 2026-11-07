package tse.lab.frontofficeciudadanos.service.rutas;

import java.io.Serializable;

/** DTO por valor: compartido por Fiscalizacion, Monitoreo y API del responsable. */
public class DatosRutaAutorizada implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Long idRuta;
    private final int alternativa;
    private final String sha256;
    private final double distanciaMetros;
    private final String geometriaGeoJson;

    public DatosRutaAutorizada(Long idRuta, int alternativa, String sha256, double distanciaMetros,
            String geometriaGeoJson) {
        this.idRuta = idRuta;
        this.alternativa = alternativa;
        this.sha256 = sha256;
        this.distanciaMetros = distanciaMetros;
        this.geometriaGeoJson = geometriaGeoJson;
    }

    public Long getIdRuta() { return idRuta; }
    public int getAlternativa() { return alternativa; }
    public String getSha256() { return sha256; }
    public double getDistanciaMetros() { return distanciaMetros; }
    public String getGeometriaGeoJson() { return geometriaGeoJson; }
}
