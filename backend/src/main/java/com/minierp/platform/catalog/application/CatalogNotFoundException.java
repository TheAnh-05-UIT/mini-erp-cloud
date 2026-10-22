package com.minierp.platform.catalog.application;

public class CatalogNotFoundException extends RuntimeException {
    public CatalogNotFoundException() {
        super("Catalog resource not found");
    }
}
