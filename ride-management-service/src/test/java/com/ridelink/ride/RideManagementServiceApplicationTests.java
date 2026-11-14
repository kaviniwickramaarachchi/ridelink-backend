package com.ridelink.ride;

import com.ridelink.ride.repository.RideRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "JWT_SECRET=ride-service-test-secret-32-bytes-minimum",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class RideManagementServiceApplicationTests {

    @MockitoBean
    private RideRepository rideRepository;

    @Test
    void contextLoads() {
    }
}
