package com.devflow.api.organization.domain;

import com.devflow.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "organization_members")
public class OrganizationMember {

    @EmbeddedId
    private OrganizationMemberId id;

    @MapsId("organizationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private OrganizationRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrganizationMember() {
    }

    public OrganizationMember(Organization organization, User user, OrganizationRole role, Instant createdAt) {
        this.organization = organization;
        this.user = user;
        this.role = role;
        this.createdAt = createdAt;
        this.id = new OrganizationMemberId(organization.getId(), user.getId());
    }

    public OrganizationMemberId getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getUser() {
        return user;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void changeRole(OrganizationRole role) {
        this.role = role;
    }
}
