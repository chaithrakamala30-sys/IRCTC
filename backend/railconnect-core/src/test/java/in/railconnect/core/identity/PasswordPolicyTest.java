package in.railconnect.core.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {
    @Test
    void acceptsLongPasswordWithThreeCharacterTypes() {
        assertThatCode(() -> IdentityService.validatePassword("RailwaySecure!2026"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordShorterThanTwelveCharacters() {
        assertThatThrownBy(() -> IdentityService.validatePassword("Rai!7short"))
                .isInstanceOf(IdentityException.class)
                .hasMessageContaining("12 characters");
    }

    @Test
    void rejectsPasswordWithFewerThanThreeCharacterTypes() {
        assertThatThrownBy(() -> IdentityService.validatePassword("alllowercase123"))
                .isInstanceOf(IdentityException.class)
                .hasMessageContaining("three character types");
    }

    @Test
    void rejectsKnownCommonPasswords() {
        assertThatThrownBy(() -> IdentityService.validatePassword("Password123!"))
                .isInstanceOf(IdentityException.class)
                .hasMessageContaining("common passwords");
    }
}
