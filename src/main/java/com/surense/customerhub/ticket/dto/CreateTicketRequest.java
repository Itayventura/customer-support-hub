package com.surense.customerhub.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @NotBlank(message = "Description must not be blank")
        @Size(max = 4000, message = "Description must be at most 4000 characters")
        String description
) {
}
