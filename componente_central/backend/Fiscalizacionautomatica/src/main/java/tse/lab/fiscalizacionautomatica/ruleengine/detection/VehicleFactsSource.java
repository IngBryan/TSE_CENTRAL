package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.io.IOException;
import java.util.Optional;

/** Where the vehicle facts come from: {@code VehicleFactsClient} (remote EJBs) in production. */
public interface VehicleFactsSource {

    /** @return empty if the vehicle is not registered in the central */
    Optional<VehicleFacts> find(String licensePlate) throws IOException;
}
