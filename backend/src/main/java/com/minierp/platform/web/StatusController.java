package com.minierp.platform.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status")
class StatusController {

    @GetMapping
    StatusResponse getStatus() {
        return new StatusResponse("mini-erp-backend", "UP");
    }

    private record StatusResponse(String application, String status) {
    }
}
