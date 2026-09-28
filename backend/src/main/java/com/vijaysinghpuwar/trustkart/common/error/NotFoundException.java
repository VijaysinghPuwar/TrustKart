package com.vijaysinghpuwar.trustkart.common.error;

/**
 * Thrown when a resource does not exist or is not visible to the caller.
 * Both cases deliberately produce the same 404 so ownership checks don't leak which IDs exist.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String what) {
        super(ErrorCode.NOT_FOUND, what + " not found.");
    }
}
