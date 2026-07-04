package com.surense.customerhub.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH semantics: {@code null} for a field means "don't change." When present, the field
 * is validated (non-blank, correct shape, within length bounds).
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 255, message = "Full name must be between 1 and 255 characters")
        String fullName,
        @Pattern(regexp = ".+", message = "Email must not be blank")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email
) {
}
