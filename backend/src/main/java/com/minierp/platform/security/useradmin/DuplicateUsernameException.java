package com.minierp.platform.security.useradmin;

/** Signals a normalized username conflict. / Bao hieu username da chuan hoa bi trung. */
public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException() { super("Duplicate normalized username"); }
}
