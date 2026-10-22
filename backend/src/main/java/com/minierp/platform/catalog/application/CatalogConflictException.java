package com.minierp.platform.catalog.application;

public class CatalogConflictException extends RuntimeException {
    private final String code;

    public CatalogConflictException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
