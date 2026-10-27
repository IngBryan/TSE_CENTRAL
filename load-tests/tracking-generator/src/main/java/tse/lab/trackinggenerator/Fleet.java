package tse.lab.trackinggenerator;

/**
 * The simulated vehicles: the load test fleet seeded in datos-prueba.sql of Frontofficeciudadanos
 * (LT00001..LT00500, GPS 100001..100500).
 */
final class Fleet {

    static final int SIZE = 500;
    private static final long FIRST_GPS_ID = 100_001;

    private Fleet() {
    }

    static String plate(int index) {
        return "LT%05d".formatted(index + 1);
    }

    static long gpsId(int index) {
        return FIRST_GPS_ID + index;
    }

    /** The route of the seeded guides. */
    static Route route() {
        return Route.MONTEVIDEO_PAYSANDU;
    }
}
