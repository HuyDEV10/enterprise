package com.huy.enterprise.common;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.OffsetDateTime; import java.util.UUID;
@Getter @Setter @MappedSuperclass
public abstract class BaseEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(nullable=false,updatable=false) private UUID id;
 @Column(name="created_at",nullable=false,updatable=false) private OffsetDateTime createdAt;
 @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
 @PrePersist void prePersist(){var now=OffsetDateTime.now(); createdAt=now; updatedAt=now;}
 @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
