package com.ridesync.server.controller;

import com.ridesync.server.dto.RideResponse;
import com.ridesync.server.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideController {
    private final RideService rides;
    private final com.ridesync.server.service.RideFinishService finishService;

    @PostMapping("/{rideSessionId}/finish")
    public com.ridesync.server.dto.FinishRideResponse finish(@AuthenticationPrincipal Long userId,
                                                          @PathVariable Long rideSessionId) {
        return finishService.finish(userId, rideSessionId);
    }

    @GetMapping("/{rideSessionId}")
    public RideResponse getRide(@AuthenticationPrincipal Long userId, @PathVariable Long rideSessionId) {
        return rides.getRide(userId, rideSessionId);
    }
}
