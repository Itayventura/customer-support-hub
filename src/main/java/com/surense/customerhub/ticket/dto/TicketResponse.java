package com.surense.customerhub.ticket.dto;

import com.surense.customerhub.ticket.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a ticket. {@code id} is the UUID {@code external_id} — the internal
 * BIGINT database id is never exposed. Customer is embedded by natural keys only.
 */
public record TicketResponse(
        UUID id,
        String title,
        String description,
        TicketStatus status,
        Instant createdAt,
        Instant updatedAt,
        CustomerRef customer
) {
    public record CustomerRef(String username, String fullName) {
    }
}
