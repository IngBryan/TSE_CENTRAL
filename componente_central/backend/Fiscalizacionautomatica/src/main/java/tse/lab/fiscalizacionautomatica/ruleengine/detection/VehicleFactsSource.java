package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.io.IOException;
import java.util.Optional;
import java.time.Instant;

/** Where the vehicle facts come from: {@code VehicleFactsClient} (remote EJBs) in production. */
public interface VehicleFactsSource {

    /** @return empty if the vehicle is not registered in the central */
    Optional<VehicleFacts> find(String licensePlate) throws IOException;

    default Optional<VehicleFacts> find(String licensePlate, Instant fechaHora) throws IOException {
        return find(licensePlate);
    }
}
