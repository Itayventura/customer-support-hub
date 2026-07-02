package com.surense.customerhub.profile.dto;

/**
 * Own-profile view — pure identity/profile data. Uses natural keys ({@code username},
 * {@code email}) and never exposes the internal database id. Roles are intentionally
 * omitted: they belong to the security context (the JWT already carries them) and are
 * not a profile attribute.
 */
public record ProfileResponse(
        String username,
        String email,
        String fullName
) {
}
