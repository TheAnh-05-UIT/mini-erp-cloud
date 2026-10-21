package com.minierp.platform.security.useradmin;

/** Signals a protected last-Admin invariant conflict. / Bao hieu xung dot bat bien Admin cuoi cung. */
public class LastActiveAdminConflictException extends RuntimeException {
    public LastActiveAdminConflictException(String message) { super(message); }
}
