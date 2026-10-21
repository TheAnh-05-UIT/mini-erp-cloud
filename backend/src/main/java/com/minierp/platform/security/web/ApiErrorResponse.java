package com.minierp.platform.security.web;

import java.time.Instant;

/** Safe API error envelope that omits internal exception details. / Error envelope API an toàn, không chứa chi tiết exception nội bộ. */
public record ApiErrorResponse(String code, String message, int status, Instant timestamp) {
}
