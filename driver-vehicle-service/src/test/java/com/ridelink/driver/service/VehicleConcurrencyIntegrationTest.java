package com.ridelink.driver.service;

import com.ridelink.driver.dto.request.CreateVehicleRequest;
import com.ridelink.driver.dto.request.UpdateVehicleStatusRequest;
import com.ridelink.driver.entity.DriverProfile;
import com.ridelink.driver.entity.Vehicle;
import com.ridelink.driver.entity.VehicleStatus;
import com.ridelink.driver.entity.VehicleType;
import com.ridelink.driver.mapper.DriverVehicleMapper;
import com.ridelink.driver.repository.DriverProfileRepository;
import com.ridelink.driver.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.datasource.url="
                + "jdbc:h2:mem:driver-concurrency;"
                + "MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VehicleService.class, DriverVehicleMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VehicleConcurrencyIntegrationTest {

    private static final long ACCOUNT_ID = 91_001L;

    private final DriverProfileRepository driverProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleService vehicleService;

    private Long driverId;

    @Autowired
    VehicleConcurrencyIntegrationTest(
            DriverProfileRepository driverProfileRepository,
            VehicleRepository vehicleRepository,
            VehicleService vehicleService) {
        this.driverProfileRepository = driverProfileRepository;
        this.vehicleRepository = vehicleRepository;
        this.vehicleService = vehicleService;
    }

    @BeforeEach
    void setUp() {
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();

        DriverProfile driver = driverProfileRepository.saveAndFlush(
                new DriverProfile(ACCOUNT_ID));
        driverId = driver.getId();
    }

    @Test
    void simultaneousVehicleAddsLeaveExactlyOneActiveVehicle()
            throws Exception {

        int requestCount = 8;
        List<Callable<Void>> operations = new ArrayList<>();

        for (int index = 0; index < requestCount; index++) {
            int vehicleNumber = index;
            operations.add(() -> {
                vehicleService.addVehicle(
                        ACCOUNT_ID,
                        vehicleRequest(
                                "TEST-ADD-" + vehicleNumber));
                return null;
            });
        }

        runSimultaneously(operations);

        List<Vehicle> vehicles = vehicleRepository
                .findAllByDriver_IdOrderByCreatedAtDesc(driverId);

        assertEquals(requestCount, vehicles.size());
        assertEquals(1L, activeVehicleCount(vehicles));
    }

    @Test
    void simultaneousVehicleAddAndReactivationLeaveOneActiveVehicle()
            throws Exception {

        DriverProfile driver = driverProfileRepository
                .findById(driverId)
                .orElseThrow();

        Vehicle inactiveVehicle = new Vehicle(
                driver,
                "TEST-INACTIVE",
                "Toyota",
                "Aqua",
                VehicleType.CAR,
                "White");
        inactiveVehicle.setVehicleStatus(VehicleStatus.INACTIVE);
        vehicleRepository.saveAndFlush(inactiveVehicle);

        List<Callable<Void>> operations = List.of(
                () -> {
                    vehicleService.addVehicle(
                            ACCOUNT_ID,
                            vehicleRequest("TEST-NEW"));
                    return null;
                },
                () -> {
                    vehicleService.updateVehicleStatus(
                            ACCOUNT_ID,
                            new UpdateVehicleStatusRequest(
                                    VehicleStatus.ACTIVE));
                    return null;
                });

        runSimultaneously(operations);

        List<Vehicle> vehicles = vehicleRepository
                .findAllByDriver_IdOrderByCreatedAtDesc(driverId);

        assertEquals(2, vehicles.size());
        assertEquals(1L, activeVehicleCount(vehicles));
    }

    private void runSimultaneously(
            List<Callable<Void>> operations) throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(operations.size());
        CountDownLatch ready =
                new CountDownLatch(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        try {
            for (Callable<Void> operation : operations) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to start concurrently");
                    }

                    return operation.call();
                }));
            }

            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady, "Concurrent operations were not ready");

            for (Future<Void> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(5, TimeUnit.SECONDS),
                    "Concurrent test executor did not stop");
        }
    }

    private CreateVehicleRequest vehicleRequest(
            String registrationNumber) {
        return new CreateVehicleRequest(
                registrationNumber,
                "Toyota",
                "Aqua",
                VehicleType.CAR,
                "White");
    }

    private long activeVehicleCount(List<Vehicle> vehicles) {
        return vehicles.stream()
                .filter(vehicle -> vehicle.getVehicleStatus()
                        == VehicleStatus.ACTIVE)
                .count();
    }
}
