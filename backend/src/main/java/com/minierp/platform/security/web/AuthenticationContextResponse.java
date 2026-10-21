package com.minierp.platform.security.web;

/** Returns only the authenticated user's safe context after login. / Chỉ trả về ngữ cảnh an toàn của user sau đăng nhập. */
public record AuthenticationContextResponse(CurrentUserResponse currentUser) {
}
