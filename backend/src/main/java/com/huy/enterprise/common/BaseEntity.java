@Getter
@Setter
@MappedSuperClass
public abstract class BaseEntity {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updateAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updateAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updateAt = OffsetDateTime.now();
    }
}