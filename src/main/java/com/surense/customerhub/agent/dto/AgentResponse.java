package com.surense.customerhub.agent.dto;

/**
 * Public view of an agent. Natural keys only — no internal database id.
 */
public record AgentResponse(
        String username,
        String email,
        String fullName
) {
}
