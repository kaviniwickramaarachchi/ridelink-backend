package com.ridelink.farepayment;

import com.ridelink.farepayment.repository.FinalFareRepository;
import com.ridelink.farepayment.repository.PaymentRepository;
import com.ridelink.farepayment.repository.ReceiptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "jwt.secret=test-secret-key-for-unit-tests-only-not-for-production-use",
        "internal.api.key=test-internal-api-key-for-unit-tests-only"
})
class FarePaymentServiceApplicationTests {

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private ReceiptRepository receiptRepository;

    @MockitoBean
    private FinalFareRepository finalFareRepository;

    @Test
    void contextLoads() {
    }
}
