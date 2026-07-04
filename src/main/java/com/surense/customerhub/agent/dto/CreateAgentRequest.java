package com.surense.customerhub.agent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAgentRequest(
        @NotBlank(message = "Username must not be blank")
        @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                 message = "Username may contain only letters, digits, dots, underscores, and hyphens")
        String username,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Full name must not be blank")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName
) {
}
