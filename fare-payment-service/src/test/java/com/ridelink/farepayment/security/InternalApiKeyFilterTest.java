package com.ridelink.farepayment.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalApiKeyFilterTest {

    private static final String EXPECTED_KEY = "correct-internal-key";

    private InternalApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter(EXPECTED_KEY);
    }

    @Test
    void internalRequest_withCorrectKey_isAllowedThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/fares/estimate");
        request.addHeader("X-Internal-Api-Key", EXPECTED_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void internalRequest_withWrongKey_isRejectedWithUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/fares/estimate");
        request.addHeader("X-Internal-Api-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void internalRequest_withMissingKey_isRejectedWithUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/fares/final");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void publicRequest_isNotAffectedByFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/fares/estimate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}