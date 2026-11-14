package com.ridelink.ride.dto;

import java.math.BigDecimal;

public record LocationResponse(
        String placeName,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
