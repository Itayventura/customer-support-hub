package com.surense.customerhub.customer.dto;

/**
 * Public view of a customer. Natural keys only — no internal database id.
 * The owning agent is embedded by natural keys as well.
 */
public record CustomerResponse(
        String username,
        String email,
        String fullName,
        AgentRef agent
) {
    public record AgentRef(String username, String fullName) {
    }
}
