package tse.lab.trackinggenerator;

/** Straight line between two cities, which the vehicle travels back and forth. */
record Route(String origin, double originLatitude, double originLongitude,
        String destination, double destinationLatitude, double destinationLongitude) {

    static final Route MONTEVIDEO_PAYSANDU =
            new Route("Montevideo", -34.9011, -56.1645, "Paysandu", -32.3171, -58.0807);

    /** @param progress 0 at the origin, 1 at the destination */
    double latitude(double progress) {
        return originLatitude + (destinationLatitude - originLatitude) * progress;
    }

    double longitude(double progress) {
        return originLongitude + (destinationLongitude - originLongitude) * progress;
    }
}
