package com.recplatform.controller;

import com.recplatform.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/{userId}")
    public List<RecommendationService.ScoredBusiness> getRecommendations(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int topK) {
        return recommendationService.recommendFor(userId, topK);
    }
}
