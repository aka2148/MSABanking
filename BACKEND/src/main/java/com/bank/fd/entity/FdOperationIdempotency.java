package com.bank.fd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Durable, actor-scoped identity for retryable FD operations. */
@Entity
@Table(name = "fd_operation_idempotency", schema = "public")
public class FdOperationIdempotency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "foi_id")
    private Long id;

    @Column(name = "foi_operation_scope", nullable = false, length = 100)
    private String operationScope;

    @Column(name = "foi_actor_key", nullable = false, length = 255)
    private String actorKey;

    @Column(name = "foi_idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "foi_request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "foi_aggregate_id")
    private Long aggregateId;

    @Column(name = "foi_status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "foi_response_reference", length = 255)
    private String responseReference;

    @Column(name = "foi_created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "foi_completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "foi_uuid", nullable = false, length = 64)
    private String uuid;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOperationScope() { return operationScope; }
    public void setOperationScope(String operationScope) { this.operationScope = operationScope; }
    public String getActorKey() { return actorKey; }
    public void setActorKey(String actorKey) { this.actorKey = actorKey; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResponseReference() { return responseReference; }
    public void setResponseReference(String responseReference) { this.responseReference = responseReference; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    @PrePersist
    void initializeMetadata() {
        if (status == null) status = "PENDING";
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (uuid == null) uuid = UUID.randomUUID().toString();
    }
}
