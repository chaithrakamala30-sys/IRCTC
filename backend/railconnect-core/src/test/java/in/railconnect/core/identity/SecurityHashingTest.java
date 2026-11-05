package in.railconnect.core.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHashingTest {
    @Test
    void keyedHashIsStableAndDoesNotExposeTheInput() {
        SecurityHashing first = new SecurityHashing("test-only-key-that-is-at-least-32-bytes");
        SecurityHashing second = new SecurityHashing("test-only-key-that-is-at-least-32-bytes");

        assertThat(first.hash("login:user@example.test"))
                .isEqualTo(second.hash("login:user@example.test"))
                .hasSize(64)
                .doesNotContain("user@example.test");
    }

    @Test
    void differentKeysProduceDifferentHashes() {
        SecurityHashing first = new SecurityHashing("test-only-key-that-is-at-least-32-bytes");
        SecurityHashing second = new SecurityHashing("a-different-test-only-key-that-is-32");

        assertThat(first.hash("login:user@example.test"))
                .isNotEqualTo(second.hash("login:user@example.test"));
    }
}
