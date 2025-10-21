package com.recplatform.service;

import com.recplatform.model.BusinessDocument;
import com.recplatform.repository.search.BusinessSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Search + secondary ranking backed by Elasticsearch, blending each result's
 * base rating with the same real-time popularity signal the recommender uses,
 * so trending businesses surface in both search and recommendations.
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private final BusinessSearchRepository businessSearchRepository;
    private final RealtimePopularityService realtimePopularityService;

    private static final double RATING_WEIGHT = 0.7;
    private static final double POPULARITY_WEIGHT = 0.3;

    public List<BusinessDocument> search(String category, int limit) {
        List<BusinessDocument> results = businessSearchRepository.findByCategoriesContaining(category);

        for (BusinessDocument doc : results) {
            double base = (doc.getStars() == null) ? 0.0 : doc.getStars() / 5.0;
            double popularity = realtimePopularityService.getPopularityScore(doc.getId());
            doc.setRankingScore(RATING_WEIGHT * base + POPULARITY_WEIGHT * popularity);
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(BusinessDocument::getRankingScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
