package com.minierp.platform.security.authorization;

/** Defines the capability boundary for User Administration only. / Xác định ranh giới quyền chỉ cho Quản trị người dùng. */
public enum UserAdministrationPermission {
    USER_READ,
    USER_CREATE,
    USER_UPDATE,
    USER_ACTIVATE,
    USER_DEACTIVATE,
    USER_ASSIGN_ROLES
}
