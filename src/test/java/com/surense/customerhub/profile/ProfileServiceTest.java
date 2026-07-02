package com.surense.customerhub.profile;

import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CurrentUserService;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import com.surense.customerhub.profile.dto.ProfileResponse;
import com.surense.customerhub.profile.dto.UpdateProfileRequest;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserRepository userRepository;

    @InjectMocks private ProfileService profileService;

    private User alice;
    private Credentials aliceCredentials;

    @BeforeEach
    void setUp() {
        alice = User.builder().id(7L).email("alice@example.com").fullName("Alice Cooper").build();
        aliceCredentials = Credentials.builder()
                .userId(7L).user(alice).username("alice").passwordHash("hash").build();
    }

    @Test
    void getMyProfileReturnsUsernameEmailAndFullName() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);

        ProfileResponse response = profileService.getMyProfile();

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.fullName()).isEqualTo("Alice Cooper");
    }

    @Test
    void updateProfileChangesFullNameOnly() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updateMyProfile(
                new UpdateProfileRequest("Alice New Name", null));

        assertThat(response.fullName()).isEqualTo("Alice New Name");
        assertThat(alice.getFullName()).isEqualTo("Alice New Name");
        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void updateProfileChangesEmailWhenNotTaken() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);
        when(userRepository.existsByEmail("alice.new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updateMyProfile(
                new UpdateProfileRequest(null, "alice.new@example.com"));

        assertThat(response.email()).isEqualTo("alice.new@example.com");
        assertThat(alice.getEmail()).isEqualTo("alice.new@example.com");
    }

    @Test
    void updateProfileRejectsDuplicateEmailWith409() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);
        when(userRepository.existsByEmail("someone.else@example.com")).thenReturn(true);

        assertThatThrownBy(() -> profileService.updateMyProfile(
                new UpdateProfileRequest(null, "someone.else@example.com")))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.CONFLICT_DUPLICATE_EMAIL);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileWithSameEmailSkipsDuplicateCheckAndSaves() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        profileService.updateMyProfile(new UpdateProfileRequest("New Name", "ALICE@example.com"));

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository).save(alice);
        assertThat(alice.getFullName()).isEqualTo("New Name");
    }

    @Test
    void updateProfileWithAllNullsIsNoop() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        profileService.updateMyProfile(new UpdateProfileRequest(null, null));

        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
        assertThat(alice.getFullName()).isEqualTo("Alice Cooper");
    }
}
