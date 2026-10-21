package com.minierp.platform.security.web;

/** Confirms server-side logout without exposing session details. / Xác nhận logout phía server mà không lộ session details. */
public record LogoutResponse(String status) {
}
