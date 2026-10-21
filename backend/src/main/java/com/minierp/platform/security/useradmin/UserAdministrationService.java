package com.minierp.platform.security.useradmin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.authorization.UserAdministrationAuthorizationService;
import com.minierp.platform.security.authorization.UserAdministrationPermission;
import com.minierp.platform.security.persistence.Role;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.persistence.RoleRepository;
import com.minierp.platform.security.persistence.UserAccount;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.persistence.UserRole;
import com.minierp.platform.security.persistence.UserRoleRepository;

/** Executes approved User Administration mutations atomically. / Thực hiện mutation Quản trị người dùng đã phê duyệt một cách nguyên tử. */
@Service
public class UserAdministrationService {

    private final UserAdministrationAuthorizationService authorization;
    private final PostgreSqlUserAdministrationLock lock;
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final UserAdministrationAuditRecorder auditRecorder;
    private final PasswordEncoder passwordEncoder;

    public UserAdministrationService(UserAdministrationAuthorizationService authorization, PostgreSqlUserAdministrationLock lock,
            UserAccountRepository users, RoleRepository roles, UserRoleRepository userRoles,
            UserAdministrationAuditRecorder auditRecorder, PasswordEncoder passwordEncoder) {
        this.authorization = authorization; this.lock = lock; this.users = users; this.roles = roles;
        this.userRoles = userRoles; this.auditRecorder = auditRecorder; this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAdministrationResult create(AuthenticatedPrincipal actor, UserAdministrationCommand.CreateUser command) {
        require(actor, UserAdministrationPermission.USER_CREATE);
        String username = UserAccount.normalizeUsername(command.username());
        if (users.existsByUsername(username)) throw new DuplicateUsernameException();
        Set<Role> assignedRoles = activeRoles(command.roleCodes());
        UserAccount target = users.saveAndFlush(UserAccount.create(UUID.randomUUID(), username,
                passwordEncoder.encode(requireNonBlank(command.initialPassword(), "initialPassword")), command.displayName()));
        assignedRoles.forEach(role -> userRoles.save(UserRole.assign(target, role, targetActor(actor))));
        audit(actor, target, "USER_CREATED", Map.of("roleCodes", roleCodes(assignedRoles)));
        return result(target, assignedRoles);
    }

    @Transactional(readOnly = true)
    public Page<UserAdministrationResult> list(AuthenticatedPrincipal actor, Pageable pageable) {
        require(actor, UserAdministrationPermission.USER_READ);
        return users.findAll(pageable).map(user -> result(user, assignedRoles(user.getId())));
    }

    @Transactional(readOnly = true)
    public UserAdministrationResult get(AuthenticatedPrincipal actor, UUID targetId) {
        require(actor, UserAdministrationPermission.USER_READ);
        UserAccount target = target(targetId);
        return result(target, assignedRoles(targetId));
    }

    @Transactional
    public UserAdministrationResult updateDisplayName(AuthenticatedPrincipal actor, UUID targetId, UserAdministrationCommand.UpdateDisplayName command) {
        require(actor, UserAdministrationPermission.USER_UPDATE);
        UserAccount target = target(targetId); target.updateDisplayName(command.displayName());
        audit(actor, target, "USER_UPDATED", Map.of("changedField", "displayName"));
        return result(target, assignedRoles(targetId));
    }

    @Transactional
    public UserAdministrationResult activate(AuthenticatedPrincipal actor, UUID targetId) {
        require(actor, UserAdministrationPermission.USER_ACTIVATE);
        UserAccount target = target(targetId); target.setActive(true);
        audit(actor, target, "USER_ACTIVATED", Map.of());
        return result(target, assignedRoles(targetId));
    }

    @Transactional
    public UserAdministrationResult deactivate(AuthenticatedPrincipal actor, UUID targetId) {
        require(actor, UserAdministrationPermission.USER_DEACTIVATE);
        rejectSelf(actor, targetId); lock.acquire();
        UserAccount target = target(targetId);
        if (isActiveAdmin(targetId) && users.countActiveAdmins() <= 1) throw new LastActiveAdminConflictException("Last active Admin cannot be deactivated");
        target.setActive(false); audit(actor, target, "USER_DEACTIVATED", Map.of());
        return result(target, assignedRoles(targetId));
    }

    @Transactional
    public UserAdministrationResult replaceRoles(AuthenticatedPrincipal actor, UUID targetId, UserAdministrationCommand.ReplaceRoles command) {
        require(actor, UserAdministrationPermission.USER_ASSIGN_ROLES);
        rejectSelf(actor, targetId); lock.acquire();
        UserAccount target = target(targetId); Set<Role> replacement = activeRoles(command.roleCodes());
        if (isActiveAdmin(targetId) && replacement.stream().noneMatch(role -> role.getCode() == RoleCode.ADMIN) && users.countActiveAdmins() <= 1)
            throw new LastActiveAdminConflictException("Last active Admin cannot lose ADMIN role");
        userRoles.deleteByIdUserId(targetId); userRoles.flush();
        replacement.forEach(role -> userRoles.save(UserRole.assign(target, role, targetActor(actor))));
        audit(actor, target, "USER_ROLES_REPLACED", Map.of("roleCodes", roleCodes(replacement)));
        return result(target, replacement);
    }

    private void require(AuthenticatedPrincipal actor, UserAdministrationPermission permission) { authorization.requirePermission(actor, permission); }
    private void rejectSelf(AuthenticatedPrincipal actor, UUID targetId) { if (actor.userId().equals(targetId)) throw new AccessDeniedException("Self mutation is forbidden"); }
    private UserAccount target(UUID id) { return users.findById(id).orElseThrow(UserNotFoundException::new); }
    private UserAccount targetActor(AuthenticatedPrincipal actor) { return users.getReferenceById(actor.userId()); }
    private boolean isActiveAdmin(UUID id) { return users.findById(id).filter(UserAccount::isActive).map(user -> assignedRoles(id).stream().anyMatch(role -> role.getCode()==RoleCode.ADMIN && role.isActive())).orElse(false); }
    private Set<Role> activeRoles(Set<RoleCode> codes) {
        if (codes == null || codes.isEmpty()) throw new IllegalArgumentException("roleCodes are required");
        Set<Role> found = codes.stream().map(code -> roles.findByCode(code).filter(Role::isActive).orElseThrow(() -> new IllegalArgumentException("Role is unavailable"))).collect(Collectors.toUnmodifiableSet());
        if (found.size()!=codes.size()) throw new IllegalArgumentException("Role is unavailable"); return found;
    }
    private Set<Role> assignedRoles(UUID id) { return userRoles.findByIdUserId(id).stream().map(UserRole::getRole).collect(Collectors.toUnmodifiableSet()); }
    private void audit(AuthenticatedPrincipal actor, UserAccount target, String event, Map<String,String> metadata) {
        auditRecorder.record(actor, target, event, metadata);
    }
    private UserAdministrationResult result(UserAccount target, Set<Role> assigned) { return new UserAdministrationResult(target.getId(), target.getUsername(), target.getDisplayName(), target.isActive(), assigned.stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet())); }
    private String roleCodes(Set<Role> roles) { return roles.stream().map(Role::getCode).map(Enum::name).sorted().collect(Collectors.joining(",")); }
    private String requireNonBlank(String value, String field) { if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" is required"); return value; }
}
