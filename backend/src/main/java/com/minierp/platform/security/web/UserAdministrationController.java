package com.minierp.platform.security.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.useradmin.UserAdministrationCommand;
import com.minierp.platform.security.useradmin.UserAdministrationService;

/** HTTP adapter for approved User Administration operations. / Adapter HTTP cho thao tac Quan tri nguoi dung da phe duyet. */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdministrationController {
    private final UserAdministrationService service;
    public UserAdministrationController(UserAdministrationService service) { this.service = service; }

    @GetMapping
    public UserPageResponse list(Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        var results = service.list(principal(authentication), PageRequest.of(page, size, Sort.by("username").ascending()))
                .map(UserResponse::from);
        return UserPageResponse.from(results);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(Authentication authentication, @Valid @RequestBody CreateUserRequest request) {
        var result = service.create(principal(authentication), new UserAdministrationCommand.CreateUser(request.username(), request.displayName(), request.initialPassword(), request.roleCodes()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + result.id())).body(UserResponse.from(result));
    }

    @GetMapping("/{userId}")
    public UserResponse get(Authentication authentication, @PathVariable UUID userId) { return UserResponse.from(service.get(principal(authentication), userId)); }

    @PatchMapping("/{userId}")
    public UserResponse update(Authentication authentication, @PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(service.updateDisplayName(principal(authentication), userId, new UserAdministrationCommand.UpdateDisplayName(request.displayName())));
    }

    @PostMapping("/{userId}/activate")
    public UserResponse activate(Authentication authentication, @PathVariable UUID userId) { return UserResponse.from(service.activate(principal(authentication), userId)); }

    @PostMapping("/{userId}/deactivate")
    public UserResponse deactivate(Authentication authentication, @PathVariable UUID userId) { return UserResponse.from(service.deactivate(principal(authentication), userId)); }

    @PutMapping("/{userId}/roles")
    public UserResponse replaceRoles(Authentication authentication, @PathVariable UUID userId, @Valid @RequestBody ReplaceUserRolesRequest request) {
        return UserResponse.from(service.replaceRoles(principal(authentication), userId, new UserAdministrationCommand.ReplaceRoles(request.roleCodes())));
    }

    private AuthenticatedPrincipal principal(Authentication authentication) { return (AuthenticatedPrincipal) authentication.getPrincipal(); }
}
