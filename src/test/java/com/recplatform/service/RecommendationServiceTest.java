package com.recplatform.service;

import com.recplatform.model.Business;
import com.recplatform.repository.BusinessRepository;
import com.recplatform.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private RealtimePopularityService realtimePopularityService;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(businessRepository, reviewRepository, realtimePopularityService);
        ReflectionTestUtils.setField(recommendationService, "contentWeight", 0.4);
        ReflectionTestUtils.setField(recommendationService, "collaborativeWeight", 0.35);
        ReflectionTestUtils.setField(recommendationService, "realtimeWeight", 0.25);
    }

    @Test
    void recommendFor_excludesAlreadyLikedBusinesses() {
        String userId = "user-1";
        when(reviewRepository.findLikedBusinessIds(userId, 4.0)).thenReturn(List.of("biz-1"));
        when(businessRepository.findAllById(List.of("biz-1"))).thenReturn(List.of(
                Business.builder().id("biz-1").categories(List.of("Restaurants", "Pizza")).build()
        ));
        when(businessRepository.findOpenBusinessesByCategories(anyList())).thenReturn(List.of(
                Business.builder().id("biz-1").categories(List.of("Restaurants", "Pizza")).build(),
                Business.builder().id("biz-2").categories(List.of("Restaurants", "Pizza")).build()
        ));
        when(reviewRepository.findCoOccurringBusinesses(eq("biz-1"), anyInt())).thenReturn(List.of());
        when(realtimePopularityService.getPopularityScore(anyString())).thenReturn(0.0);

        List<RecommendationService.ScoredBusiness> results = recommendationService.recommendFor(userId, 10);

        assertThat(results).extracting(r -> r.business().getId()).doesNotContain("biz-1");
        assertThat(results).extracting(r -> r.business().getId()).contains("biz-2");
    }

    @Test
    void recommendFor_handlesUserWithNoHistoryWithoutError() {
        String userId = "new-user";
        when(reviewRepository.findLikedBusinessIds(userId, 4.0)).thenReturn(List.of());
        when(businessRepository.findOpenBusinessesByCategories(anyList())).thenReturn(List.of(
                Business.builder().id("biz-3").categories(List.of("Cafes")).build()
        ));
        when(realtimePopularityService.getPopularityScore(anyString())).thenReturn(0.2);

        List<RecommendationService.ScoredBusiness> results = recommendationService.recommendFor(userId, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).business().getId()).isEqualTo("biz-3");
    }

    @Test
    void recommendFor_respectsTopKLimit() {
        String userId = "user-2";
        when(reviewRepository.findLikedBusinessIds(userId, 4.0)).thenReturn(List.of());
        when(businessRepository.findOpenBusinessesByCategories(anyList())).thenReturn(List.of(
                Business.builder().id("biz-a").categories(List.of("Cafes")).build(),
                Business.builder().id("biz-b").categories(List.of("Cafes")).build(),
                Business.builder().id("biz-c").categories(List.of("Cafes")).build()
        ));
        when(realtimePopularityService.getPopularityScore(anyString())).thenReturn(0.0);

        List<RecommendationService.ScoredBusiness> results = recommendationService.recommendFor(userId, 2);

        assertThat(results).hasSize(2);
    }
}
