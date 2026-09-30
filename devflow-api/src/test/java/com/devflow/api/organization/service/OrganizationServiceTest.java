package com.devflow.api.organization.service;

import com.devflow.api.organization.api.AddOrganizationMemberRequest;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, memberRepository, userRepository);
    }

    @Test
    void rejectsOrganizationAccessForNonMember() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, organizationId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(404);
                    assertThat(exception.getCode()).isEqualTo("ORGANIZATION_NOT_FOUND");
                });
    }

    @Test
    void memberCannotAddAnotherMember() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        OrganizationMember actor = member(organizationId, userId, OrganizationRole.MEMBER);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId))
                .thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.addMember(userId, organizationId,
                new AddOrganizationMemberRequest(UUID.randomUUID(), OrganizationRole.MEMBER)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getStatus().value()).isEqualTo(403));
    }

    @Test
    void adminCannotChangeOwnerRole() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        OrganizationMember actor = member(organizationId, adminId, OrganizationRole.ADMIN);
        OrganizationMember owner = member(organizationId, ownerId, OrganizationRole.OWNER);
        when(userRepository.existsById(adminId)).thenReturn(true);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, adminId))
                .thenReturn(Optional.of(actor));
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, ownerId))
                .thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.updateMember(adminId, organizationId, ownerId,
                new UpdateOrganizationMemberRequest(OrganizationRole.MEMBER)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getStatus().value()).isEqualTo(403));
    }

    @Test
    void cannotRemoveLastOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        OrganizationMember owner = member(organizationId, ownerId, OrganizationRole.OWNER);
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, ownerId))
                .thenReturn(Optional.of(owner));
        when(memberRepository.countByIdOrganizationIdAndRole(organizationId, OrganizationRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.removeMember(ownerId, organizationId, ownerId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getCode()).isEqualTo("LAST_OWNER");
                });
    }

    @Test
    void ownerCanUpdateMemberRole() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        OrganizationMember owner = member(organizationId, ownerId, OrganizationRole.OWNER);
        OrganizationMember target = member(organizationId, memberId, OrganizationRole.MEMBER);
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, ownerId))
                .thenReturn(Optional.of(owner));
        when(memberRepository.findByIdOrganizationIdAndIdUserId(organizationId, memberId))
                .thenReturn(Optional.of(target));
        when(memberRepository.saveAndFlush(target)).thenReturn(target);

        OrganizationMember updated = service.updateMember(ownerId, organizationId, memberId,
                new UpdateOrganizationMemberRequest(OrganizationRole.ADMIN));

        assertThat(updated.getRole()).isEqualTo(OrganizationRole.ADMIN);
        verify(memberRepository).saveAndFlush(target);
    }

    private OrganizationMember member(UUID organizationId, UUID userId, OrganizationRole role) {
        Organization organization = new Organization("Test", "test-" + organizationId, Instant.now(), Instant.now());
        User user = new User("user-" + userId + "@example.com", "User", null, true, Instant.now(), Instant.now());
        return new OrganizationMember(organization, user, role, Instant.now());
    }
}
