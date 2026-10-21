package com.minierp.platform.security.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable paging response for User Administration. / Response phan trang on dinh cho Quan tri nguoi dung. */
public record UserPageResponse(List<UserResponse> content, int page, int size, long totalElements, int totalPages) {
    static UserPageResponse from(Page<UserResponse> page) {
        return new UserPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
