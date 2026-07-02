package com.surense.customerhub.profile;

import com.surense.customerhub.profile.dto.ProfileResponse;
import com.surense.customerhub.profile.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ProfileResponse getMe() {
        return profileService.getMyProfile();
    }

    @PatchMapping("/me")
    public ProfileResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateMyProfile(request);
    }
}
