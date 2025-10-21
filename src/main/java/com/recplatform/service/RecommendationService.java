package com.recplatform.service;

import com.recplatform.model.Business;
import com.recplatform.repository.BusinessRepository;
import com.recplatform.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The recommendation engine: a hybrid of content-based filtering (category
 * overlap with a user's highly-rated businesses), item-item collaborative
 * filtering (co-occurrence in other users' highly-rated reviews), and a
 * real-time popularity signal fed by live Kafka activity. Scores are blended
 * with configurable weights and the top-K candidates are returned.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final BusinessRepository businessRepository;
    private final ReviewRepository reviewRepository;
    private final RealtimePopularityService realtimePopularityService;

    @Value("${app.recommendation.weights.content}")
    private double contentWeight;

    @Value("${app.recommendation.weights.collaborative}")
    private double collaborativeWeight;

    @Value("${app.recommendation.weights.realtime-popularity}")
    private double realtimeWeight;

    private static final List<String> FOOD_CATEGORIES = List.of(
            "Restaurants", "Food", "Cafes", "Coffee & Tea", "Bakeries",
            "Bars", "Fast Food", "Pizza", "Sandwiches", "Breakfast & Brunch"
    );

    private static final double LIKED_THRESHOLD_STARS = 4.0;
    private static final int CO_OCCURRENCE_CANDIDATES_PER_LIKED_BUSINESS = 20;

    @Cacheable(value = "recommendations", key = "#userId + '-' + #topK")
    public List<ScoredBusiness> recommendFor(String userId, int topK) {
        List<String> likedBusinessIds = reviewRepository.findLikedBusinessIds(userId, LIKED_THRESHOLD_STARS);

        Map<String, Double> contentScores = computeContentScores(likedBusinessIds);
        Map<String, Double> collaborativeScores = computeCollaborativeScores(likedBusinessIds);

        List<Business> candidates = businessRepository.findOpenBusinessesByCategories(FOOD_CATEGORIES);

        return candidates.stream()
                .filter(b -> !likedBusinessIds.contains(b.getId()))
                .map(b -> {
                    double content = contentScores.getOrDefault(b.getId(), 0.0);
                    double collaborative = collaborativeScores.getOrDefault(b.getId(), 0.0);
                    double realtime = realtimePopularityService.getPopularityScore(b.getId());
                    double finalScore = contentWeight * content
                            + collaborativeWeight * collaborative
                            + realtimeWeight * realtime;
                    return new ScoredBusiness(b, finalScore);
                })
                .sorted(Comparator.comparingDouble(ScoredBusiness::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private Map<String, Double> computeContentScores(List<String> likedBusinessIds) {
        if (likedBusinessIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Business> likedBusinesses = businessRepository.findAllById(likedBusinessIds);

        Map<String, Long> userCategoryFrequency = likedBusinesses.stream()
                .filter(b -> b.getCategories() != null)
                .flatMap(b -> b.getCategories().stream())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));

        List<Business> allFoodBusinesses = businessRepository.findOpenBusinessesByCategories(FOOD_CATEGORIES);

        Map<String, Double> scores = new HashMap<>();
        for (Business candidate : allFoodBusinesses) {
            if (candidate.getCategories() == null || candidate.getCategories().isEmpty()) {
                continue;
            }
            double overlap = candidate.getCategories().stream()
                    .mapToDouble(c -> userCategoryFrequency.getOrDefault(c, 0L))
                    .sum();
            double norm = Math.sqrt(candidate.getCategories().size()) * Math.sqrt(userCategoryFrequency.size() + 1);
            scores.put(candidate.getId(), norm == 0 ? 0 : overlap / norm);
        }
        return normalize(scores);
    }

    private Map<String, Double> computeCollaborativeScores(List<String> likedBusinessIds) {
        Map<String, Double> aggregate = new HashMap<>();
        for (String businessId : likedBusinessIds) {
            List<Object[]> coOccurring = reviewRepository.findCoOccurringBusinesses(
                    businessId, CO_OCCURRENCE_CANDIDATES_PER_LIKED_BUSINESS);
            for (Object[] row : coOccurring) {
                String coBusinessId = (String) row[0];
                Number count = (Number) row[1];
                aggregate.merge(coBusinessId, count.doubleValue(), Double::sum);
            }
        }
        return normalize(aggregate);
    }

    private Map<String, Double> normalize(Map<String, Double> scores) {
        if (scores.isEmpty()) {
            return scores;
        }
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (max == 0) {
            return scores;
        }
        Map<String, Double> normalized = new HashMap<>();
        scores.forEach((k, v) -> normalized.put(k, v / max));
        return normalized;
    }

    public record ScoredBusiness(Business business, double score) {}
}
