package com.chronosq.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;

    public DashboardController(
            DashboardQueryService dashboardQueryService
    ) {
        this.dashboardQueryService = dashboardQueryService;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        return dashboardQueryService.loadDashboard();
    }
}
