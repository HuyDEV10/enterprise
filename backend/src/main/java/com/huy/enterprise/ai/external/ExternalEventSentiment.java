package com.huy.enterprise.ai.external;

import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.supplier.Supplier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter @Setter @Entity
@Table(name="external_event_sentiments", uniqueConstraints=@UniqueConstraint(name="uk_event_sentiment_target", columnNames={"external_event_id","target_key"}))
public class ExternalEventSentiment extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="external_event_id", nullable=false)
    private ExternalEvent externalEvent;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="supplier_id")
    private Supplier supplier;
    @Column(name="target_entity", nullable=false, length=300) private String targetEntity;
    @Column(name="target_key", nullable=false, length=300) private String targetKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private EntitySentiment sentiment;
    @Column(nullable=false, precision=8, scale=6) private BigDecimal confidence;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false, columnDefinition="jsonb")
    private Map<String,Double> probabilities = new LinkedHashMap<>();
    @Column(nullable=false, length=10) private String language;
    @Column(name="needs_review", nullable=false) private boolean needsReview;
    @Column(name="review_reason", length=100) private String reviewReason;
    @Column(name="model_version", nullable=false, length=100) private String modelVersion;
    @Column(name="input_truncated", nullable=false) private boolean inputTruncated;
    @Column(name="source_text", nullable=false, columnDefinition="TEXT") private String sourceText;
    @Column(name="source_text_sha256", nullable=false, length=64) private String sourceTextSha256;
    @Column(name="match_method", nullable=false, length=50) private String matchMethod;
    @Column(name="analyzed_at", nullable=false) private OffsetDateTime analyzedAt;
}
