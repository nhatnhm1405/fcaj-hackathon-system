package com.fcaj.hackathon.event;

public record AccountApprovalEmailEvent(
        String email,
        String fullName,
        boolean approved
) {
}
