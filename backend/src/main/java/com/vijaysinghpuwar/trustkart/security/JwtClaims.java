package com.vijaysinghpuwar.trustkart.security;

public final class JwtClaims {

    public static final String ISSUER = "trustkart";
    public static final String AUDIENCE = "trustkart-api";
    public static final String USER_ID = "uid";
    public static final String SESSION_ID = "sid";
    public static final String NAME = "name";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "perms";

    private JwtClaims() {}
}
