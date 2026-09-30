package com.devflow.api.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "external_auth_id", unique = true, length = 255)
    private String externalAuthId;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String email, String displayName, String externalAuthId, boolean active, Instant createdAt, Instant updatedAt) {
        this(email, displayName, externalAuthId, null, active, createdAt, updatedAt);
    }

    public User(String email, String displayName, String externalAuthId, String passwordHash,
                boolean active, Instant createdAt, Instant updatedAt) {
        this.email = email;
        this.displayName = displayName;
        this.externalAuthId = externalAuthId;
        this.passwordHash = passwordHash;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExternalAuthId() {
        return externalAuthId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
