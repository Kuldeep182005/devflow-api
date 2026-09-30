package com.devflow.api.organization.service;

import com.devflow.api.organization.api.AddOrganizationMemberRequest;
import com.devflow.api.organization.api.CreateOrganizationRequest;
import com.devflow.api.organization.api.UpdateOrganizationMemberRequest;
import com.devflow.api.organization.domain.Organization;
import com.devflow.api.organization.domain.OrganizationMember;
import com.devflow.api.organization.domain.OrganizationRole;
import com.devflow.api.persistence.organization.OrganizationMemberRepository;
import com.devflow.api.persistence.organization.OrganizationRepository;
import com.devflow.api.persistence.user.UserRepository;
import com.devflow.api.user.domain.User;
import com.devflow.api.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Organization create(UUID principalId, CreateOrganizationRequest request) {
        User owner = userRepository.findById(principalId)
                .orElseThrow(() -> unauthorized("Authenticated user no longer exists"));
        String slug = request.slug().trim().toLowerCase(java.util.Locale.ROOT);
        if (organizationRepository.findBySlug(slug).isPresent()) {
            throw conflict("ORGANIZATION_SLUG_ALREADY_EXISTS", "An organization with this slug already exists");
        }

        Instant now = Instant.now();
        Organization organization = new Organization(request.name().trim(), slug, now, now);
        try {
            organization = organizationRepository.saveAndFlush(organization);
            memberRepository.saveAndFlush(new OrganizationMember(organization, owner, OrganizationRole.OWNER, now));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ORGANIZATION_SLUG_ALREADY_EXISTS", "An organization with this slug already exists");
        }
        return organization;
    }

    @Transactional(readOnly = true)
    public List<Organization> list(UUID principalId) {
        requireUser(principalId);
        return organizationRepository.findAllAccessibleTo(principalId);
    }

    @Transactional(readOnly = true)
    public Organization get(UUID principalId, UUID organizationId) {
        return requireActorMembership(principalId, organizationId).getOrganization();
    }

    @Transactional(readOnly = true)
    public Page<OrganizationMember> listMembers(UUID principalId, UUID organizationId, Pageable pageable) {
        requireActorMembership(principalId, organizationId);
        return memberRepository.findAllByOrganizationId(organizationId, pageable);
    }

    @Transactional
    public OrganizationMember addMember(
            UUID principalId, UUID organizationId, AddOrganizationMemberRequest request) {
        OrganizationMember actor = requireActorMembership(principalId, organizationId);
        requireManager(actor);
        validateRole(request.role());
        if (actor.getRole() == OrganizationRole.ADMIN && request.role() == OrganizationRole.OWNER) {
            throw forbidden("Only an owner can grant the OWNER role");
        }
        if (!userRepository.existsById(request.userId())) {
            throw notFound("USER_NOT_FOUND", "The requested user does not exist");
        }
        if (memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, request.userId()).isPresent()) {
            throw conflict("MEMBERSHIP_ALREADY_EXISTS", "The user is already a member of this organization");
        }

        OrganizationMember member = new OrganizationMember(
                actor.getOrganization(),
                userRepository.getReferenceById(request.userId()),
                request.role(),
                Instant.now());
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("MEMBERSHIP_ALREADY_EXISTS", "The user is already a member of this organization");
        }
    }

    @Transactional
    public OrganizationMember updateMember(
            UUID principalId,
            UUID organizationId,
            UUID userId,
            UpdateOrganizationMemberRequest request) {
        OrganizationMember actor = requireActorMembership(principalId, organizationId);
        requireManager(actor);
        validateRole(request.role());
        OrganizationMember target = memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId)
                .orElseThrow(() -> notFound("MEMBERSHIP_NOT_FOUND", "The user is not a member of this organization"));
        ensureCanManage(actor, target);
        if (actor.getRole() == OrganizationRole.ADMIN && request.role() == OrganizationRole.OWNER) {
            throw forbidden("Only an owner can grant the OWNER role");
        }
        if (target.getRole() == OrganizationRole.OWNER
                && request.role() != OrganizationRole.OWNER
                && memberRepository.countByIdOrganizationIdAndRole(organizationId, OrganizationRole.OWNER) <= 1) {
            throw conflict("LAST_OWNER", "An organization must have at least one owner");
        }
        target.changeRole(request.role());
        return memberRepository.saveAndFlush(target);
    }

    @Transactional
    public void removeMember(UUID principalId, UUID organizationId, UUID userId) {
        OrganizationMember actor = requireActorMembership(principalId, organizationId);
        requireManager(actor);
        OrganizationMember target = memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId)
                .orElseThrow(() -> notFound("MEMBERSHIP_NOT_FOUND", "The user is not a member of this organization"));
        ensureCanManage(actor, target);
        if (target.getRole() == OrganizationRole.OWNER
                && memberRepository.countByIdOrganizationIdAndRole(organizationId, OrganizationRole.OWNER) <= 1) {
            throw conflict("LAST_OWNER", "An organization must have at least one owner");
        }
        memberRepository.delete(target);
    }

    private OrganizationMember requireActorMembership(UUID principalId, UUID organizationId) {
        requireUser(principalId);
        return memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, principalId)
                .orElseThrow(() -> notFound("ORGANIZATION_NOT_FOUND", "The organization does not exist"));
    }

    private void requireUser(UUID principalId) {
        if (principalId == null || !userRepository.existsById(principalId)) {
            throw unauthorized("Authentication is required");
        }
    }

    private void requireManager(OrganizationMember actor) {
        if (actor.getRole() != OrganizationRole.OWNER && actor.getRole() != OrganizationRole.ADMIN) {
            throw forbidden("Only owners and admins can manage organization members");
        }
    }

    private void ensureCanManage(OrganizationMember actor, OrganizationMember target) {
        if (actor.getRole() == OrganizationRole.ADMIN && target.getRole() != OrganizationRole.MEMBER) {
            throw forbidden("Admins can only manage members");
        }
    }

    private void validateRole(OrganizationRole role) {
        if (role == null || role == OrganizationRole.VIEWER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE",
                    "Role must be OWNER, ADMIN, or MEMBER");
        }
    }

    private ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    private ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
