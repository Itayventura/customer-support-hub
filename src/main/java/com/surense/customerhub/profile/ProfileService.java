package com.surense.customerhub.profile;

import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CurrentUserService;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import com.surense.customerhub.profile.dto.ProfileResponse;
import com.surense.customerhub.profile.dto.UpdateProfileRequest;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class ProfileService {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public ProfileService(
            CurrentUserService currentUserService,
            UserRepository userRepository
    ) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile() {
        return toResponse(currentUserService.currentCredentials());
    }

    @Transactional
    public ProfileResponse updateMyProfile(UpdateProfileRequest request) {
        Credentials credentials = currentUserService.currentCredentials();
        User user = credentials.getUser();

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new ApiException(ErrorCode.CONFLICT_DUPLICATE_EMAIL);
            }
            user.setEmail(request.email());
        }

        if (request.fullName() != null && !Objects.equals(request.fullName(), user.getFullName())) {
            user.setFullName(request.fullName());
        }

        userRepository.save(user);
        return toResponse(credentials);
    }

    private ProfileResponse toResponse(Credentials credentials) {
        User user = credentials.getUser();
        return new ProfileResponse(credentials.getUsername(), user.getEmail(), user.getFullName());
    }
}
