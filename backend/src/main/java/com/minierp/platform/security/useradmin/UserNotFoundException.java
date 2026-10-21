package com.minierp.platform.security.useradmin;

/** Signals that an administration target does not exist. / Bao hieu user can quan tri khong ton tai. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() { super("User not found"); }
}
