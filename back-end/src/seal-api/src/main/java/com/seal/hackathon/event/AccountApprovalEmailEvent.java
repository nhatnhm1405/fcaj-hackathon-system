package com.seal.hackathon.event;

public record AccountApprovalEmailEvent(
        String email,
        String fullName,
        boolean approved
) {
}
