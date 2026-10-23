package com.huy.enterprise.common;

import com.huy.enterprise.ai.external.GdeltUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltUnavailableHandlerTest {
    @Test void unavailableFeedHasSpecific503Contract() {
        var request = new MockHttpServletRequest("POST", "/api/ai/external-events/gdelt/latest");
        var result = new ApiExceptionHandler().gdeltUnavailable(
                new GdeltUnavailableException("GDELT feed is temporarily unavailable; retry later"), request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("GDELT_UNAVAILABLE");
        assertThat(result.getBody().status()).isEqualTo(503);
        assertThat(result.getBody().path()).isEqualTo(request.getRequestURI());
    }
}
