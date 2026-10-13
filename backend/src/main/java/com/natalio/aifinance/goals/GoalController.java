package com.natalio.aifinance.goals;

import com.natalio.aifinance.dashboard.DashboardResponse.SavingsGoal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {
    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavingsGoal create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateGoalRequest request
    ) {
        return goalService.create(UUID.fromString(jwt.getSubject()), request);
    }

    @DeleteMapping("/{goalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID goalId) {
        goalService.delete(UUID.fromString(jwt.getSubject()), goalId);
    }

    @PostMapping("/{goalId}/movements")
    public SavingsGoal moveMoney(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID goalId,
        @Valid @RequestBody GoalMovementRequest request
    ) {
        return goalService.moveMoney(UUID.fromString(jwt.getSubject()), goalId, request);
    }
}
