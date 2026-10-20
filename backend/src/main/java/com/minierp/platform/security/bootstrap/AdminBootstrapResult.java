package com.minierp.platform.security.bootstrap;

/**
 * Describes whether the guarded bootstrap was disabled, skipped, or created the first Admin. / Mô tả bootstrap bị tắt, bị bỏ qua hoặc đã tạo Admin đầu tiên.
 */
public enum AdminBootstrapResult {
    DISABLED,
    SKIPPED_EXISTING_USER,
    CREATED
}
