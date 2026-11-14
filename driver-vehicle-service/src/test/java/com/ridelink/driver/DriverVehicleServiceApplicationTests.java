package com.ridelink.driver;

import com.ridelink.driver.repository.DriverProfileRepository;
import com.ridelink.driver.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "security.jwt.secret=test-jwt-secret-that-is-at-least-32-characters",
        "security.internal-api-key=test-internal-api-key"
})
class DriverVehicleServiceApplicationTests {

    @MockitoBean
    private DriverProfileRepository driverProfileRepository;

    @MockitoBean
    private VehicleRepository vehicleRepository;

    @Test
    void contextLoads() {
    }
}