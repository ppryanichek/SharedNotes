package com.eugene.sharednotes.common;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link Tokens}.
 */
class TokensTest {

    @Nested
    class Hash {

        @Test
        void shouldReturnSameHashForSameInput() {
            String token = "some-token";

            assertThat(Tokens.hash(token)).isEqualTo(Tokens.hash(token));
        }

        @Test
        void shouldReturnDifferentHashesForDifferentInputs() {
            assertThat(Tokens.hash("token-a")).isNotEqualTo(Tokens.hash("token-b"));
        }

        @Test
        void shouldNotReturnOriginalToken() {
            String token = "some-token";

            assertThat(Tokens.hash(token)).isNotEqualTo(token);
        }

        @Test
        void shouldReturn64CharacterHexString() {
            assertThat(Tokens.hash("some-token")).hasSize(64);
        }
    }

    @Nested
    class GenerateRandomToken {

        @Test
        void shouldReturnDifferentTokensOnEachCall() {
            assertThat(Tokens.generateRandomToken()).isNotEqualTo(Tokens.generateRandomToken());
        }

        @Test
        void shouldReturnUrlSafeToken() {
            assertThat(Tokens.generateRandomToken()).doesNotContain("+", "/", "=");
        }

        @Test
        void shouldReturn43CharacterToken() {
            assertThat(Tokens.generateRandomToken()).hasSize(43);
        }
    }
}
