package com.recplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

/** Elasticsearch document backing search + secondary ranking of businesses. */
@Document(indexName = "businesses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword)
    private List<String> categories;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Double)
    private Double stars;

    @Field(type = FieldType.Integer)
    private Integer reviewCount;

    @Field(type = FieldType.GeoPoint)
    private GeoPointDto location;

    /** Blended relevance + real-time popularity score, computed at query time. */
    @Field(type = FieldType.Double)
    private Double rankingScore;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GeoPointDto {
        private double lat;
        private double lon;
    }
}
