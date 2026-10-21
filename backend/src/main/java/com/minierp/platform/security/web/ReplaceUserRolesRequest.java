package com.minierp.platform.security.web;

import java.util.Set;
import jakarta.validation.constraints.NotEmpty;
import com.minierp.platform.security.persistence.RoleCode;

/** Replaces the complete explicit role set. / Thay the toan bo tap role ro rang. */
public record ReplaceUserRolesRequest(@NotEmpty Set<RoleCode> roleCodes) { }
