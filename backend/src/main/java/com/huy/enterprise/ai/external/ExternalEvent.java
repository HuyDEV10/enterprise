package com.huy.enterprise.ai.external;

import com.huy.enterprise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "external_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_event_source_id",
                columnNames = {"external_source", "external_event_id"}))
public class ExternalEvent extends BaseEntity {
    @Column(name = "external_source", nullable = false, length = 50)
    private String externalSource;

    @Column(name = "external_event_id", nullable = false, length = 100)
    private String externalEventId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_code", length = 20)
    private String eventCode;

    @Column(name = "event_base_code", length = 20)
    private String eventBaseCode;

    @Column(name = "event_root_code", length = 20)
    private String eventRootCode;

    @Column(name = "actor1_name")
    private String actor1Name;

    @Column(name = "actor2_name")
    private String actor2Name;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(length = 100)
    private String country;

    private String location;

    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(name = "goldstein_score", precision = 7, scale = 3)
    private BigDecimal goldsteinScore;

    @Column(name = "avg_tone", precision = 9, scale = 4)
    private BigDecimal avgTone;

    @Column(name = "num_mentions")
    private Integer numMentions;

    @Column(name = "num_sources")
    private Integer numSources;

    @Column(name = "num_articles")
    private Integer numArticles;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EntitySentiment sentiment = EntitySentiment.NOT_ANALYZED;

    @Column(name = "sentiment_confidence", precision = 5, scale = 4)
    private BigDecimal sentimentConfidence;

    @Column(name = "sentiment_model_version", length = 100)
    private String sentimentModelVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ExternalEventSignal signal;
}
