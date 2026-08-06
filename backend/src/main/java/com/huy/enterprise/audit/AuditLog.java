package com.huy.enterprise.audit;

@Getter
@Setter
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;
    @Column(nullable = false, length = 100)
    private String action;
    @Column(name = "entyTpye", nullable = false, length = 100)
    private String entityType;
    @Column(name = "entity_id")
    private UUID entityId;
    private String description;
    @Column(name = "create_at", nullable = false, updatable = false)
    private OffsetDateTime createAt;

    @PrePersist
    void prePersist() {
        createAt = OffsetDateTime.now();
    }
}
