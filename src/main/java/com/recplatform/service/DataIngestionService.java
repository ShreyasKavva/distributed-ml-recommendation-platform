package com.recplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recplatform.model.Business;
import com.recplatform.model.BusinessDocument;
import com.recplatform.repository.search.BusinessSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bulk-loads business and review data (in the same schema Yelp's dataset
 * uses) into Postgres and Elasticsearch. Data comes from the bundled
 * synthetic generator (scripts/data-generation/generate_dataset.py) rather
 * than Yelp directly - no scraping, no license gate, fully reproducible.
 *
 * Disabled by default. Enable with:
 *   java -jar app.jar --app.ingestion.enabled=true --app.ingestion.data-path=/path/to/data
 *
 * Uses JDBC batching (not one-row-at-a-time JPA saves) and the Elasticsearch
 * bulk API (via saveAll) because this needs to comfortably handle 500K+ rows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataIngestionService implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final BusinessSearchRepository businessSearchRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ingestion.enabled}")
    private boolean ingestionEnabled;

    @Value("${app.ingestion.data-path}")
    private String dataPath;

    @Value("${app.ingestion.batch-size}")
    private int batchSize;

    @Override
    public void run(String... args) throws Exception {
        if (!ingestionEnabled) {
            return;
        }
        ingestBusinesses();
        ingestReviews();
    }

    // ---------------------------------------------------------------------
    // Businesses -> Postgres (JDBC batch) + Elasticsearch (bulk index)
    // ---------------------------------------------------------------------

    private void ingestBusinesses() throws IOException {
        Path businessFile = Path.of(dataPath, "businesses.json");
        if (!Files.exists(businessFile)) {
            log.warn("{} not found - skipping business ingestion. Run scripts/data-generation/generate_dataset.py to create it (see README).", businessFile);
            return;
        }

        log.info("Starting business ingestion from {}", businessFile);
        List<Business> batch = new ArrayList<>(batchSize);
        long total = 0;

        try (BufferedReader reader = Files.newBufferedReader(businessFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Business business = parseBusiness(line);
                if (business == null) continue;
                batch.add(business);
                total++;

                if (batch.size() >= batchSize) {
                    flushBusinessBatch(batch);
                    batch.clear();
                    if (total % 50_000 == 0) {
                        log.info("Ingested {} businesses so far...", total);
                    }
                }
            }
            if (!batch.isEmpty()) {
                flushBusinessBatch(batch);
            }
        }

        log.info("Business ingestion complete. Total businesses ingested: {}", total);
    }

    private Business parseBusiness(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            List<String> categories = (node.hasNonNull("categories"))
                    ? List.of(node.get("categories").asText().split(",\\s*"))
                    : List.of();

            return Business.builder()
                    .id(node.get("business_id").asText())
                    .name(node.get("name").asText())
                    .address(node.path("address").asText(null))
                    .city(node.path("city").asText(null))
                    .state(node.path("state").asText(null))
                    .postalCode(node.path("postal_code").asText(null))
                    .latitude(node.path("latitude").isMissingNode() ? null : node.get("latitude").asDouble())
                    .longitude(node.path("longitude").isMissingNode() ? null : node.get("longitude").asDouble())
                    .stars(node.path("stars").isMissingNode() ? null : node.get("stars").asDouble())
                    .reviewCount(node.path("review_count").isMissingNode() ? null : node.get("review_count").asInt())
                    .isOpen(node.path("is_open").isMissingNode() || node.get("is_open").asInt() == 1)
                    .categories(categories)
                    .build();
        } catch (Exception e) {
            log.warn("Skipping malformed business record: {}", e.getMessage());
            return null;
        }
    }

    private void flushBusinessBatch(List<Business> batch) {
        String sql = "INSERT INTO businesses "
                + "(business_id, name, address, city, state, postal_code, latitude, longitude, stars, review_count, is_open) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (business_id) DO NOTHING";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Business b = batch.get(i);
                ps.setString(1, b.getId());
                ps.setString(2, b.getName());
                ps.setString(3, b.getAddress());
                ps.setString(4, b.getCity());
                ps.setString(5, b.getState());
                ps.setString(6, b.getPostalCode());
                setNullableDouble(ps, 7, b.getLatitude());
                setNullableDouble(ps, 8, b.getLongitude());
                setNullableDouble(ps, 9, b.getStars());
                if (b.getReviewCount() != null) ps.setInt(10, b.getReviewCount()); else ps.setNull(10, Types.INTEGER);
                ps.setBoolean(11, Boolean.TRUE.equals(b.getIsOpen()));
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });

        for (Business b : batch) {
            insertCategories(b);
        }

        List<BusinessDocument> documents = batch.stream().map(this::toDocument).collect(Collectors.toList());
        businessSearchRepository.saveAll(documents);
    }

    private void insertCategories(Business b) {
        if (b.getCategories() == null || b.getCategories().isEmpty()) return;
        String sql = "INSERT INTO business_categories (business_id, category) VALUES (?, ?) ON CONFLICT DO NOTHING";
        List<String> categories = b.getCategories();
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setString(1, b.getId());
                ps.setString(2, categories.get(i));
            }

            @Override
            public int getBatchSize() {
                return categories.size();
            }
        });
    }

    private BusinessDocument toDocument(Business b) {
        BusinessDocument.GeoPointDto geo = (b.getLatitude() != null && b.getLongitude() != null)
                ? new BusinessDocument.GeoPointDto(b.getLatitude(), b.getLongitude())
                : null;

        return BusinessDocument.builder()
                .id(b.getId())
                .name(b.getName())
                .categories(b.getCategories())
                .city(b.getCity())
                .stars(b.getStars())
                .reviewCount(b.getReviewCount())
                .location(geo)
                .rankingScore(b.getStars() == null ? 0.0 : b.getStars() / 5.0)
                .build();
    }

    // ---------------------------------------------------------------------
    // Reviews -> Postgres (JDBC batch). Reviews are what the recommender's
    // content-based and collaborative signals are actually computed from.
    // ---------------------------------------------------------------------

    private void ingestReviews() throws IOException {
        Path reviewFile = Path.of(dataPath, "reviews.json");
        if (!Files.exists(reviewFile)) {
            log.warn("{} not found - skipping review ingestion. Recommendations will be empty without review data.", reviewFile);
            return;
        }

        log.info("Starting review ingestion from {}", reviewFile);
        List<ParsedReview> batch = new ArrayList<>(batchSize);
        long total = 0;

        try (BufferedReader reader = Files.newBufferedReader(reviewFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                ParsedReview review = parseReview(line);
                if (review == null) continue;
                batch.add(review);
                total++;

                if (batch.size() >= batchSize) {
                    flushReviewBatch(batch);
                    batch.clear();
                    if (total % 100_000 == 0) {
                        log.info("Ingested {} reviews so far...", total);
                    }
                }
            }
            if (!batch.isEmpty()) {
                flushReviewBatch(batch);
            }
        }

        log.info("Review ingestion complete. Total reviews ingested: {}", total);
    }

    private ParsedReview parseReview(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            LocalDateTime date = node.hasNonNull("date")
                    ? LocalDateTime.parse(node.get("date").asText().trim().replace(" ", "T"))
                    : null;

            return new ParsedReview(
                    node.get("review_id").asText(),
                    node.get("user_id").asText(),
                    node.get("business_id").asText(),
                    node.path("stars").isMissingNode() ? null : node.get("stars").asDouble(),
                    date,
                    node.path("useful").isMissingNode() ? null : node.get("useful").asInt()
            );
        } catch (Exception e) {
            log.warn("Skipping malformed review record: {}", e.getMessage());
            return null;
        }
    }

    private void flushReviewBatch(List<ParsedReview> batch) {
        String sql = "INSERT INTO reviews (review_id, user_id, business_id, stars, review_date, useful_count) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (review_id) DO NOTHING";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ParsedReview r = batch.get(i);
                ps.setString(1, r.id());
                ps.setString(2, r.userId());
                ps.setString(3, r.businessId());
                setNullableDouble(ps, 4, r.stars());
                if (r.date() != null) ps.setTimestamp(5, Timestamp.valueOf(r.date())); else ps.setNull(5, Types.TIMESTAMP);
                if (r.usefulCount() != null) ps.setInt(6, r.usefulCount()); else ps.setNull(6, Types.INTEGER);
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value != null) {
            ps.setDouble(index, value);
        } else {
            ps.setNull(index, Types.DOUBLE);
        }
    }

    private record ParsedReview(
            String id,
            String userId,
            String businessId,
            Double stars,
            LocalDateTime date,
            Integer usefulCount
    ) {}
}
